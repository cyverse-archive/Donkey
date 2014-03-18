(ns donkey.services.fileio.controllers
  (:use [clj-jargon.init :only [with-jargon]]
        [clj-jargon.item-info]
        [clj-jargon.permissions]
        [clj-jargon.users :only [user-exists?]]
        [clojure-commons.error-codes]
        [clojure-commons.validators]
        [donkey.util.config]
        [donkey.util.validators]
        [donkey.util.transformers :only [add-current-user-to-map]]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [donkey.services.fileio.actions :as actions]
            [donkey.services.fileio.controllers :as fileio]
            [donkey.services.filesystem.common-paths :as cp]
            [donkey.services.filesystem.stat :as stat]
            [donkey.util.config :as config]
            [cheshire.core :as json]
            [clj-jargon.item-ops :as jargon-ops]
            [clojure-commons.file-utils :as ft]
            [clojure.string :as string]
            [donkey.util.ssl :as ssl]
            [clojure.tools.logging :as log]
            [ring.util.response :as rsp-utils]
            [cemerick.url :as url-parser]))

(defn in-stream
  [address]
  (try+
   (ssl/input-stream address)
   (catch java.io.IOException e
     (throw+ {:error_code ERR_INVALID_URL
              :url address
              :msg (.getMessage e)}))))

(defn gen-uuid []
  (str (java.util.UUID/randomUUID)))

(defn store
  [cm istream filename user dest-dir]
  (actions/store cm istream user (ft/path-join dest-dir filename)))

(defn store-irods
  [{stream :stream orig-filename :filename}]
  (let [uuid     (gen-uuid)
        filename (str orig-filename "." uuid)
        user     (irods-user)
        home     (irods-home)
        temp-dir (fileio-temp-dir)]
    (if-not (cp/good-string? orig-filename)
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :path orig-filename}))
    (with-jargon (jargon-cfg) [cm]
      (store cm stream filename user temp-dir))))

(defn download
  [req-params]
  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string? :path string?})
    (actions/download (:user params) (:path params))))

(defn upload
  [req-params req-multipart]
  (log/info "Detected params: " req-params)
  (validate-map req-params {"file" string? "user" string? "dest" string?})
  (let [user    (get req-params "user")
        dest    (get req-params "dest")
        up-path (get req-multipart "file")]
    (if-not (cp/good-string? up-path)
      {:status 500
       :body   (json/generate-string
                 {:error_code ERR_BAD_OR_MISSING_FIELD
                  :path       up-path})}
      (actions/upload user up-path dest))))

(defn url-filename
  [address]
  (let [parsed-url (url-parser/url address)]
    (when-not (:protocol parsed-url)
      (throw+ {:error_code ERR_INVALID_URL
                :url address}))

    (when-not (:host parsed-url)
      (throw+ {:error_code ERR_INVALID_URL
               :url address}))

    (if-not (string/blank? (:path parsed-url))
      (ft/basename (:path parsed-url))
      (:host parsed-url))))

(defn urlupload
  [req-params req-body]
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:dest string? :address string?})
    (let [user    (:user params)
          dest    (string/trim (:dest body))
          addr    (string/trim (:address body))
          fname   (url-filename addr)]
      (log/warn (str "User: " user))
      (log/warn (str "Dest: " dest))
      (log/warn (str "Fname: " fname))
      (log/warn (str "Addr: " addr))
      (with-open [istream (in-stream addr)]
        (log/warn "connection to" addr "successfully established"))
      (actions/urlimport user addr fname dest))))

(defn save
  [req-params req-body]
  (log/info "Detected params: " req-params)
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:dest string? :content string?})
    (let [user      (:user params)
          dest      (string/trim (:dest body))
          tmp-file  (str dest "." (gen-uuid))
          content   (:content body)
          file-size (count (.getBytes content "UTF-8"))]
      (with-jargon (jargon-cfg) [cm]
        (when-not (user-exists? cm user)
          (throw+ {:user       user
                   :error_code ERR_NOT_A_USER}))

        (when-not (exists? cm dest)
          (throw+ {:error_code ERR_DOES_NOT_EXIST
                   :path       dest}))

        (when-not (is-writeable? cm user dest)
          (throw+ {:error_code ERR_NOT_WRITEABLE
                   :path       dest}))

        (when (> file-size (config/fileio-max-edit-file-size))
          (throw+ {:error_code "ERR_FILE_SIZE_TOO_LARGE"
                   :path       dest
                   :size       file-size}))

        ;; Jargon will delete dest before writing its new contents, which will cause the old version
        ;; of the file to be put into the Trash. So rename dest to tmp-file, then force-delete
        ;; tmp-file after a successful save of the new contents.
        (jargon-ops/move cm dest tmp-file :user user :admin-users (irods-admins))
        (try+
          (with-in-str content
            (actions/save cm *in* user dest))
          (jargon-ops/delete cm tmp-file true)
          (catch Object e
            (log/debug e)
            (jargon-ops/move cm tmp-file dest :user user :admin-users (irods-admins))
            (throw+)))

        {:file (stat/path-stat user dest)}))))

(defn saveas
  [req-params req-body]
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:dest string? :content string?})
    (let [user (:user params)
          dest (string/trim (:dest body))
          cont (:content body)]
      (with-jargon (jargon-cfg) [cm]
        (when-not (user-exists? cm user)
          (throw+ {:user       user
                   :error_code ERR_NOT_A_USER}))

        (when-not (exists? cm (ft/dirname dest))
          (throw+ {:error_code ERR_DOES_NOT_EXIST
                   :path       (ft/dirname dest)}))

        (when-not (is-writeable? cm user (ft/dirname dest))
          (throw+ {:error_code ERR_NOT_WRITEABLE
                   :path       (ft/dirname dest)}))

        (when (exists? cm dest)
          (throw+ {:error_code ERR_EXISTS
                   :path       dest}))

        (with-in-str cont
          (actions/store cm *in* user dest))

        {:file (stat/path-stat user dest)}))))
