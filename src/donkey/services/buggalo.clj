(ns donkey.services.buggalo
  (:use [clojure.java.io :only [copy file]]
        [clojure-commons.file-utils :only [with-temp-dir-in]]
        [donkey.util.config
         :only [tree-parser-url scruffian-base-url nibblonian-base-url riak-base-url
                tree-url-bucket]]
        [donkey.services.buggalo.nexml :only [is-nexml? extract-trees-from-nexml]]
        [donkey.util.service :only [success-response]]
        [donkey.auth.user-attributes :only [current-user]]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [cemerick.url :as curl]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.error-codes :as ce]
            [clojure-commons.file-utils :as ft]
            [donkey.util.nibblonian :as nibblonian]
            [donkey.util.scruffian :as scruffian])
  (:import [java.security MessageDigest DigestInputStream]
           [org.forester.io.parsers.util ParserUtils PhylogenyParserException]
           [org.forester.io.writers PhylogenyWriter]
           [org.forester.phylogeny PhylogenyMethods]))

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
                   :body         (cheshire/generate-string body)}})))

(defn- get-tree-viewer-url
  "Obtains a tree viewer URL for a single tree file."
  [f]
  (log/debug "obtaining a tree viewer URL for" (.getName f))
  (let [label     (string/replace (.getName f) #"[.]tre$" "")
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
      (cheshire/parse-string (:body res) true))))

(defn- save-tree-urls
  "Saves the tree URLs for a file."
  [tree-urls metaurl]
  (let [res (client/post metaurl
                         {:body         (cheshire/generate-string tree-urls)
                          :content-type :json}
                         {:throw-exceptions false})]
    (when-not (<= 200 (:status res) 299)
      (log/warn "unable to save tree URLs -" (:body res)))))

(defn- save-tree-metaurl
  "Saves the URL used to obtain the tree URLs in the AVUs for the file."
  [path metaurl]
  (let [urlpath (:path (curl/url metaurl))]
    (try+
      (nibblonian/save-tree-metaurl path urlpath)
      (catch [:error_code ce/ERR_REQUEST_FAILED] {:keys [body]}
        (log/warn "unable to save the tree metaurl for" path "-"
                  (cheshire/generate-string (cheshire/parse-string body) {:pretty true})))
      (catch Exception e
        (log/warn e "unable to save the tree metaurl for" path)))))

(defn- urlize
  [url-path]
  (if-not (or (.startsWith url-path "http") (.startsWith url-path "https"))
    (str (curl/url (riak-base-url) :path url-path))
    url-path))

(defn- get-existing-tree-urls
  "Obtains existing tree URLs for either a file stored in the iPlant data store
   or a SHA1 hash obtained from the contents of a file."
  ([sha1]
     (log/debug "searching for existing tree URLs for SHA1 hash" sha1)
     (retrieve-tree-urls-from (metaurl-for sha1)))
  ([user path]
     (log/debug "searching for existing tree URLs for user" user "and path" path)
     (when-let [metaurl (nibblonian/get-tree-metaurl user path)]
       (retrieve-tree-urls-from (urlize metaurl))))
  ([sha1 user path]
     (log/debug "searching for existing tree URLs for SHA1 hash" sha1)
     (let [metaurl (metaurl-for sha1)]
       (when-let [urls (retrieve-tree-urls-from metaurl)]
         (save-tree-metaurl path metaurl)
         urls))))

(defn- save-tree-file
  "Saves a tree file in the local file system."
  [dir index tree]
  (let [writer    (PhylogenyWriter/createPhylogenyWriter)
        tree-name (.getName tree)
        file-name (if (string/blank? tree-name)
                    (str "tree_" index ".tre")
                    (str tree-name ".tre"))
        out-file  (file dir file-name)]
    (.toNewHampshire writer tree false true out-file)
    out-file))

(defn- extract-trees-from-other
  "Extracts trees from all supported formats except for NeXML."
  [dir infile]
  (let [parser (ParserUtils/createParserDependingFileContents infile false)
        trees  (seq (PhylogenyMethods/readPhylogenies parser infile))]
    (mapv (partial save-tree-file dir) (range) trees)))

(defn- extract-trees
  "Extracts trees from a tree file."
  [dir infile]
  (if (is-nexml? infile)
    (extract-trees-from-nexml dir infile)
    (extract-trees-from-other dir infile)))

(defn- get-tree-viewer-urls
  "Obtains the tree viewer URLs for the contents of a tree file."
  [dir infile]
  (log/debug "getting new tree URLs")
  (try
    (mapv get-tree-viewer-url (extract-trees dir infile))
    (catch PhylogenyParserException e
      (log/warn e "assuming that the given file contains no trees")
      [])))

(defn- build-response-map
  "Builds the map to use when formatting the response body."
  [urls]
  (assoc (nibblonian/format-tree-urls urls) :action "tree_manifest"))

(defn- get-and-save-tree-viewer-urls
  "Gets the tree-viewer URLs for a file and stores them in Riak.  If the username and path to the
   file are also provided then the Riak URL will also be storeed in the AVUs for the file."
  ([dir infile sha1]
     (let [urls    (build-response-map (get-tree-viewer-urls dir infile))
           metaurl (metaurl-for sha1)]
       (save-tree-urls urls metaurl)
       urls))
  ([path user dir infile sha1]
     (let [urls    (build-response-map (get-tree-viewer-urls dir infile))
           metaurl (metaurl-for sha1)]
       (save-tree-urls urls metaurl)
       (save-tree-metaurl path metaurl)
       urls)))

(defn tree-urls-response
  "Formats the response for one of the tree viewer URL services."
  [resp]
  (success-response {:urls (:tree-urls resp)}))

(defn tree-viewer-urls-for
  "Obtains the tree viewer URLs for a request body."
  [body {:keys [refresh]}]
  (log/info "getting tree viewer URLs for a request body")
  (with-temp-dir-in dir (file "/tmp") "tv" temp-dir-creation-failure
    (let [infile (file dir "data.txt")
          sha1   (save-file body infile)]
      (if-let [tree-urls (when-not refresh (get-existing-tree-urls sha1))]
        (do (log/debug "found existing tree URLs for" sha1)
            (tree-urls-response tree-urls))
        (do (log/debug "generating new URLs for" sha1)
            (tree-urls-response (get-and-save-tree-viewer-urls dir infile sha1)))))))

(defn tree-viewer-urls
  "Obtains the tree viewer URLs for a tree file in iRODS."
  ([path]
     (tree-viewer-urls path (:shortUsername current-user) {}))
  ([path user {:keys [refresh]}]
     (log/debug "obtaining tree URLs for user" user "and path" path)
     (tree-urls-response
      (or (and (not refresh) (get-existing-tree-urls user path))
          (with-temp-dir-in dir (file "/tmp") "tv" temp-dir-creation-failure
            (let [infile (file dir "data.txt")
                  body   (scruffian/download user path)
                  sha1   (save-file body infile)]
              (or (and (not refresh) (get-existing-tree-urls sha1 user path))
                  (get-and-save-tree-viewer-urls path user dir infile sha1))))))))
