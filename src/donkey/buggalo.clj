(ns donkey.buggalo
  (:use [clojure.data.json :only [json-str]]
        [clojure.java.io :only [copy file]]
        [clojure.java.shell :only [sh]]
        [clojure-commons.file-utils :only [with-temp-dir-in]]
        [donkey.config
         :only [buggalo-path supported-tree-formats tree-parser-url
                scruffian-base-url nibblonian-base-url]]
        [donkey.service :only [success-response]]
        [donkey.user-attributes :only [current-user]]
        [slingshot.slingshot :only [throw+]])
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.nibblonian :as nibblonian]
            [clojure-commons.scruffian :as scruffian])
  (:import [java.io FilenameFilter]
           [java.security MessageDigest DigestInputStream]))

(defn- temp-dir-creation-failure
  "Handles the failure to create a temporary directory."
  [parent prefix base]
  (throw+ {:type   :temp-dir-failure
           :parent parent
           :prefix prefix
           :base   base}))

(defn- list-tree-files
  "Lists the tree files in the provided directory."
  [dir]
  (sort (filter #(re-find #".tre$" (.getName %))
                (seq (.listFiles dir)))))

(defn- tree-parser-error
  "Throws an exception indicating that the tree parser encountered an error."
  [res]
  (let [body {:action  "tree_manifest"
              :message "unable to parse tree data"
              :details (:body res)
              :success false}]
   (throw+ {:type :error-status
            :res  {:status       (:status res)
                   :content-type :json
                   :body         (json-str body)}})))

(defn- get-tree-viewer-url
  "Obtains a tree viewer URL for a single tree file."
  [f]
  (let [label     (first (string/split (.getName f) #"[.]" 2))
        multipart [{:name "name"       :content label}
                   {:name "newickData" :content f}]
        res       (client/post (tree-parser-url)
                               {:multipart        multipart
                                :throw-exceptions false})]
    (if (< 199 (:status res) 300)
      (nibblonian/format-tree-url label (string/trim (:body res)))
      (tree-parser-error res))))

(defn save-file
  "Saves the contents of an input stream to a file and returns the SHA1 hash of
   the file contents."
  [contents infile]
  (let [digest   (MessageDigest/getInstance "SHA1")
        hex-byte #(Integer/toHexString (bit-and 0xff %))]
    (copy (DigestInputStream. contents digest) infile)
    (apply str (map hex-byte (seq (.digest digest))))))

(defn get-tree-viewer-urls
  "Obtains the tree viewer URLs for the contents of a tree file."
  [contents]
  (with-temp-dir-in dir (file "/tmp") "tv" temp-dir-creation-failure
    (let [buggalo  (buggalo-path)
          formats  (supported-tree-formats)
          infile   (file dir "data.txt")
          inpath   (.getPath infile)
          sha1     (save-file contents infile)
          _        (log/warn "sha1 =" sha1)
          results  (map #(assoc (sh buggalo "-i" inpath "-f" % :dir dir) :fmt %)
                        formats)
          success  #(first (filter (comp zero? :exit) results))
          details  #(into {} (map (fn [{:keys [fmt err]}] [fmt err]) results))]
      (if (success)
        (mapv get-tree-viewer-url (list-tree-files dir))
        (throw+ {:type    :tree-file-parse-err
                 :details (details)})))))

(defn- build-response-map
  "Builds the map to use when formatting the response body."
  [urls]
  (assoc (nibblonian/format-tree-urls urls) :action "tree_manifest"))

(defn tree-viewer-urls-for
  "Obtains the tree viewer URLs for a request body."
  [body]
  (success-response (build-response-map (get-tree-viewer-urls body))))

(defn tree-viewer-urls
  "Obtains the tree viewer URLs for a tree file in iRODS."
  ([path]
     (tree-viewer-urls path (:shortUsername current-user)))
  ([path user]
     (let [contents  (scruffian/download (scruffian-base-url) user path)
           tree-urls (get-tree-viewer-urls contents)]
       (nibblonian/delete-tree-urls (nibblonian-base-url) user path)
       (nibblonian/save-tree-urls (nibblonian-base-url) user path tree-urls)
       (success-response (build-response-map tree-urls)))))
