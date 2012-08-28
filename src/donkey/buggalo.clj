(ns donkey.buggalo
  (:use [clojure.data.json :only [json-str]]
        [clojure.java.io :only [file]]
        [clojure.java.shell :only [sh]]
        [clojure-commons.file-utils :only [with-temp-dir]]
        [donkey.config
         :only [buggalo-path supported-tree-formats tree-parser-url]]
        [donkey.service :only [success-response]]
        [slingshot.slingshot :only [throw+]])
  (:require [clj-http.client :as client]
            [clojure.string :as string])
  (:import [java.io FilenameFilter]))

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
  (let [files (sort (filter #(re-find #".tre$" (.getName %))
                            (seq (.listFiles dir))))]
    (dorun (map #(println (.getPath %)) files))
    files))

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

(defn- tree-url
  "Creates a tree URL element."
  [label url]
  {:label label
   :url   url})

(defn- get-tree-viewer-url
  "Obtains a tree viewer URL for a single tree file."
  [f]
  (let [tree   (slurp f)
        label  (first (string/split (.getName f) #"[.]" 2))
        newick (str (slurp f) ";")
        _      (println newick)
        res    (client/post (tree-parser-url)
                            {:form-params      {:name       label
                                                :newickData newick}
                             :throw-exceptions false})]
    (if (< 199 (:status res) 300)
      (tree-url label (string/trim (:body res)))
      (tree-parser-error res))))

(defn get-tree-viewer-urls
  "Obtains the tree viewer URLs for the contents of a tree file."
  [contents]
  (with-temp-dir dir "tv" temp-dir-creation-failure
    (let [buggalo (buggalo-path)
          formats (supported-tree-formats)
          results (map #(assoc (sh buggalo "-f" % :in contents :dir dir) :fmt %)
                       formats)
          success #(first (filter (comp zero? :exit) results))
          details #(into {} (map (fn [{:keys [fmt err]}] [fmt err]) results))]
      (if (success)
        (vec (map get-tree-viewer-url (list-tree-files dir)))
        (throw+ {:type    :tree-file-parse-err
                 :details (details)})))))

(defn- build-response-map
  "Builds the map to use when formatting the response body."
  [urls]
  {:action    "tree_manifest"
   :tree-urls urls})

(defn tree-viewer-urls-for
  "Obtains the tree viewer URLs for a request body."
  [body]
  (success-response (build-response-map (get-tree-viewer-urls body))))

(defn tree-viewer-urls
  "Obtains the tree viewer URLs for a tree file in iRODS."
  [path])
