(ns donkey.services.fileio.actions
  (:use [clj-jargon.jargon]
        [donkey.util.config]
        [clojure-commons.error-codes]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [cemerick.url :as url] 
            [cheshire.core :as json]
            [clojure-commons.file-utils :as ft]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clj-http.client :as client]
            [donkey.services.garnish.irods :as filetype]
            [ring.util.response :as rsp-utils]))

(defn set-meta
  [path attr value unit]
  (with-jargon (jargon-cfg) [cm]
    (set-metadata cm path attr value unit)))

(defn scruffy-copy
  [cm user istream dest-path]
  (let [ostream (output-stream cm dest-path)]
    (try
      (io/copy istream ostream)
      (finally
        (.close istream)
        (.close ostream)
        (set-owner cm dest-path user)
        (let [guessed-type (:type (filetype/preview-auto-type user dest-path))]
          (if-not (or (nil? guessed-type) (empty? guessed-type))
            (filetype/auto-add-type cm user dest-path)))))
    {:id          dest-path
     :permissions (dataobject-perm-map cm user dest-path)}))

(defn store
  [cm istream user dest-path]
  (log/info "In store function for " user dest-path)
  (let [ddir (ft/dirname dest-path)]
    (when-not (exists? cm ddir)
      (mkdirs cm ddir))

    (when-not (is-writeable? cm user ddir)
      (log/error (str "Directory " ddir " is not writeable."))
      (throw+ {:error_code ERR_NOT_WRITEABLE
               :path ddir} ))

    (scruffy-copy cm user istream dest-path)
    (log/info "store function after copy.")
    dest-path))

(defn- get-istream
  [user file-path]
  (with-jargon (jargon-cfg) [cm]
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user       user}))
    
    (when-not (exists? cm file-path)
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :path       file-path}))
    
    (when-not (is-readable? cm user file-path)
      (throw+ {:error_code ERR_NOT_READABLE
               :user       user
               :path       file-path}))

    (if (= (file-size cm file-path) 0)
      ""
      (input-stream cm file-path))))

(defn- new-filename
  [tmp-path]
  (string/join "." (drop-last (string/split (ft/basename tmp-path) #"\."))))

(defn upload
  [user tmp-path final-path]
  (log/info "In upload for " user tmp-path final-path)
  (with-jargon (jargon-cfg) [cm]
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))

    (when-not (exists? cm final-path)
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :id final-path}))

    (when-not (is-writeable? cm user final-path)
      (throw+ {:error_code ERR_NOT_WRITEABLE
               :id final-path}))

    (let [new-fname (new-filename tmp-path)
          new-path  (ft/path-join final-path new-fname)]
      (if (exists? cm new-path) (delete cm new-path))
      (move cm tmp-path new-path :user user :admin-users (irods-admins) :skip-source-perms? true)
      (set-owner cm new-path user)
      {:file 
       {:id new-path
        :label         (ft/basename new-path)
        :permissions   (dataobject-perm-map cm user new-path)
        :date-created  (created-date cm new-path)
        :date-modified (lastmod-date cm new-path)
        :file-size     (str (file-size cm new-path))}})))

(defn url-encoded?
  [string-to-check]
  (re-seq #"\%[A-Fa-f0-9]{2}" string-to-check))

(defn url-encode-path
  [path-to-encode]
  (string/join "/"
   (mapv
    #(if-not (url-encoded? %1)
       (url/url-encode %1)
       %1)
    (string/split path-to-encode #"\/"))))

(defn url-encode-url
  [url-to-encode]
  (let [full-url (url/url url-to-encode)]
    (str (assoc full-url :path (url-encode-path (:path full-url))))))

(defn- jex-urlimport
  [user address filename dest-path]
  (let [curl-dir  (ft/dirname (fileio-curl-path))
        curl-name (ft/basename (fileio-curl-path))
        job-name  (str "url_import_" filename)
        job-desc  (str "URL Import of " filename " from " address)
        submission-json (json/generate-string
                          {:name                  job-name
                           :type                  "data"
                           :description           job-desc
                           :output_dir            dest-path
                           :create_output_subdir  false
                           :uuid                  (str (java.util.UUID/randomUUID))
                           :monitor_transfer_logs false
                           :skip-parent-meta      true
                           :username              user
                           :file-metadata 
                           [{:attr  "ipc-url-import"
                             :value address
                             :unit  "Import URL"}]
                           :steps 
                           [{:component
                             {:location curl-dir
                              :name     curl-name}
                             :config
                             {:params
                              [{:name "-o"
                                :value filename
                                :order 1}
                               {:name (str "'" (url-encode-url address) "'")
                                :value ""
                                :order 2}]
                              :input []
                              :output
                              [{:name         "logs"
                                :property     "logs"
                                :type         "File"
                                :multiplicity "collection"
                                :retain       false}]}}]})]
    (log/warn "Curl directory: " curl-dir)
    (log/warn "Curl name: " curl-name)
    (log/warn "Submission JSON:\n" submission-json)
    submission-json))

(defn- jex-send
  [body]
  (client/post
    (jex-base-url)
    {:content-type :json
     :body body}))

(defn urlimport
  "Pushes out an import job to the JEX.

   Parameters:
     user - string containing the username of the user that requested the
        import.
     address - string containing the URL of the file to be imported.
     filename - the filename of the file being imported.
     dest-path - irods path indicating the directory the file should go in."
  [user address filename dest-path]
  (with-jargon (jargon-cfg) [cm]
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user       user}))

    (when-not (is-writeable? cm user dest-path)
      (throw+ {:error_code ERR_NOT_WRITEABLE
               :user       user
               :path       dest-path}))

    (when (exists? cm (ft/path-join dest-path filename))
      (throw+ {:error_code ERR_EXISTS
               :path (ft/path-join dest-path filename)}))

    (let [decoded-filename (if (url-encoded? filename) 
                             (url/url-decode filename) filename)
          req-body         (jex-urlimport user address decoded-filename dest-path)
          {jex-status :status jex-body :body} (jex-send req-body)]
      (log/warn "Status from JEX: " jex-status)
      (log/warn "Body from JEX: " jex-body)

      (when (not= jex-status 200)
        (throw+ {:msg        jex-body
                 :error_code ERR_REQUEST_FAILED}))

      {:msg    "Upload scheduled."
       :url    address
       :label  decoded-filename
       :dest   dest-path})))

(defn download
  "Returns a response map filled out with info that lets the client download
   a file."
  [user file-path]
  (log/debug "In download.")
  (let [istream (get-istream user file-path)]
    (-> {:status 200
         :body istream}
      (rsp-utils/header
        "Content-Disposition"
        (str "attachment; filename=\"" (ft/basename file-path) "\"")))))
