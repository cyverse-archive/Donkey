(ns donkey.buggalo
  (:use [clojure.data.json :only [read-json json-str]]
        [clojure.java.io :only [copy file]]
        [clojure.java.shell :only [sh]]
        [clojure-commons.file-utils :only [with-temp-dir-in]]
        [donkey.config
         :only [buggalo-path supported-tree-formats tree-parser-url
                scruffian-base-url nibblonian-base-url riak-base-url
                tree-url-bucket]]
        [donkey.service :only [success-response]]
        [donkey.user-attributes :only [current-user]]
        [slingshot.slingshot :only [throw+]])
  (:require [cemerick.url :as curl]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.nibblonian :as nibblonian]
            [clojure-commons.scruffian :as scruffian])
  (:import [java.io FilenameFilter]
           [java.security MessageDigest DigestInputStream]))

(defn- metaurl-for
  "Builds the meta-URL for to use when saving tree files in Riak.  The SHA1 hash
   of the contents of the tree file is used as the key in Riak."
  [sha1]
  (->> [(riak-base-url) (tree-url-bucket) sha1]
       (map #(string/replace % #"^/|/$" ""))
       (string/join "/")))

(defn- temp-dir-creation-failure
  "Handles the failure to create a temporary directory."
  [parent prefix base]
  (log/error "failed to create a temporary directory: base =" base)
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
  (log/error "the tree parser encountered an error:" (:body res))
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
  (log/debug "obtaining a tree viewer URL for" (.getName f))
  (let [label     (first (string/split (.getName f) #"[.]" 2))
        multipart [{:name "name"       :content label}
                   {:name "newickData" :content f}]
        res       (client/post (tree-parser-url)
                               {:multipart        multipart
                                :throw-exceptions false})]
    (if (< 199 (:status res) 300)
      (nibblonian/format-tree-url label (string/trim (:body res)))
      (tree-parser-error res))))

(defn- save-file
  "Saves the contents of an input stream to a file and returns the SHA1 hash of
   the file contents."
  [contents infile]
  (let [digest   (MessageDigest/getInstance "SHA1")
        hex-byte #(Integer/toHexString (bit-and 0xff %))]
    (copy (DigestInputStream. contents digest) infile)
    (apply str (map hex-byte (seq (.digest digest))))))

(defn- retrieve-tree-urls-from
  "Retrieves tree URLs from their external storage."
  [url]
  (log/debug "retrieving tree URLs from" url)
  (let [res (client/get url {:throw-exceptions false})]
    (when (<= 200 (:status res) 299)
      (read-json (:body res)))))

(defn- save-tree-urls
  "Saves the tree URLs for a file."
  [tree-urls metaurl]
  (let [res (client/post metaurl
                         {:body         (json-str tree-urls)
                          :content-type :json}
                         {:throw-exceptions false})]
    (when-not (<= 200 (:status res) 299)
      (log/warn "unable to save tree URLs -" (:body res)))))

(defn- save-tree-metaurl
  "Saves the URL used to obtain the tree URLs in the AVUs for the file."
  [user path metaurl]
  (let [base    (nibblonian-base-url)
        urlpath (:path (curl/url metaurl))
        res     (nibblonian/save-tree-metaurl base user path urlpath)]
    (when-not (<= 200 (:status res) 299)
      (log/warn "unable to save the tree metaurl for" path "-" (:body res)))))

(defn- get-existing-tree-urls
  "Obtains existing tree URLs for either a file stored in the iPlant data store
   or a SHA1 hash obtained from the contents of a file."
  ([sha1]
     (log/debug "searching for existing tree URLs for SHA1 hash" sha1)
     (retrieve-tree-urls-from (metaurl-for sha1)))
  ([user path]
     (log/debug "searching for existing tree URLs for user" user "and path" path)
     (when-let [metaurl (nibblonian/get-tree-metaurl (nibblonian-base-url) user path)]
       (retrieve-tree-urls-from metaurl)))
  ([sha1 user path]
     (log/debug "searching for existing tree URLs for SHA1 hash" sha1)
     (let [metaurl (metaurl-for sha1)]
       (when-let [urls (retrieve-tree-urls-from metaurl)]
         (log/debug "saving existing tree meta URLs in AVUs for" path)
         (save-tree-metaurl user path metaurl)
         urls))))

(defn- get-tree-viewer-urls
  "Obtains the tree viewer URLs for the contents of a tree file."
  [dir infile]
  (log/debug "getting new tree URLs")
  (let [buggalo   (buggalo-path)
        inpath    (.getPath infile)
        parse-fmt #(assoc (sh buggalo "-i" inpath "-f" % :dir dir) :fmt %)
        results   (map parse-fmt (supported-tree-formats))
        success   #(first (filter (comp zero? :exit) results))
        details   #(into {} (map (fn [{:keys [fmt err]}] [fmt err]) results))]
    (if (success)
      (mapv get-tree-viewer-url (list-tree-files dir))
      (do (log/error "unable to get tree viewer URLs:" details)
          (throw+ {:type    :tree-file-parse-err
                   :details (details)})))))

(defn- build-response-map
  "Builds the map to use when formatting the response body."
  [urls]
  (assoc (nibblonian/format-tree-urls urls) :action "tree_manifest"))

(defn- get-and-save-tree-viewer-urls
  "Gets the tree-viewer URLs for a file, stores them in Riak and stores the Riak
   URL in the AVUs for the file."
  ([dir infile sha1]
     (let [urls    (build-response-map (get-tree-viewer-urls dir infile))
           metaurl (metaurl-for sha1)]
       (save-tree-urls urls metaurl)))
  ([path user dir infile sha1]
     (let [urls    (build-response-map (get-tree-viewer-urls dir infile))
           metaurl (metaurl-for sha1)]
       (save-tree-urls urls metaurl)
       (save-tree-metaurl user path metaurl))))

(defn tree-viewer-urls-for
  "Obtains the tree viewer URLs for a request body."
  [body]
  (log/info "getting tree viewer URLs for a request body")
  (with-temp-dir-in dir (file "/tmp") "tv" temp-dir-creation-failure
    (let [infile (file dir "data.txt")
          sha1   (save-file body infile)]
      (if-let [tree-urls (get-existing-tree-urls sha1)]
        (do (log/debug "found existing tree URLs for" sha1)
            (success-response tree-urls))
        (do (log/debug "generating new URLs for" sha1)
            (success-response (get-and-save-tree-viewer-urls dir infile sha1)))))))

(defn tree-viewer-urls
  "Obtains the tree viewer URLs for a tree file in iRODS."
  ([path]
     (tree-viewer-urls path (:shortUsername current-user)))
  ([path user & {:keys [refresh]}]
     (log/info "obtaining tree URLs for user" user "and path" path)
     (success-response
      (or (and (not refresh) (get-existing-tree-urls user path))
          (with-temp-dir-in dir (file "/tmp") "tv" temp-dir-creation-failure
            (let [infile (file dir "data.txt")
                  body   (scruffian/download (scruffian-base-url) user path)
                  sha1   (save-file body infile)]
              (or (and (not refresh) (get-existing-tree-urls sha1 user path))
                  (get-and-save-tree-viewer-urls path user dir infile sha1))))))))
