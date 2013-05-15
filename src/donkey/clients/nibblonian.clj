(ns donkey.clients.nibblonian
  (:use [donkey.util.config]
        [donkey.util.service :only [build-url-with-query]]
        [donkey.util.transformers :only [add-current-user-to-map]]
        [slingshot.slingshot :only [throw+]])
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as client]))

(defn- nibblonian-url
  "Builds a URL that can be used to send a request to Nibblonian."
  [query & components]
  (let [query (add-current-user-to-map query)]
    (apply build-url-with-query (nibblonian-base-url) query components)))

(defn- handle-nibblonian-resp
  "Handles a response from Nibblonian."
  [res f]
  (if (< 199 (:status res) 300)
    (f res)
    (throw+ {:type :error-status :res res})))

(defn- nibblonian-get
  "Forwards a GET request to Nibblonian."
  [query f & components]
  (let [url (apply nibblonian-url query components)
        res (client/get url {:throw-exceptions false})]
    (handle-nibblonian-resp res f)))

(defn- nibblonian-post
  "Forwards a POST request to Nibblonian."
  [query body-map f & components]
  (let [url (apply nibblonian-url query components)
        res (client/post url {:body             (cheshire/encode body-map)
                              :content-type     :json
                              :throw-exceptions false})]
    (handle-nibblonian-resp res f)))

(defn home-dir
  "Determines the home folder for the current user."
  []
  (nibblonian-get {} :body "home"))

(defn create
  "Creates a directory."
  [path]
  (let [query {}
        body  {:path path}
        f     #(:path (cheshire/decode (:body %) true))]
    (nibblonian-post query body f "directory" "create")))

(defn exists?
  "Determines whether or not a path exists."
  [path]
  (let [query {}
        body  {:paths [path]}
        f     #(get-in (cheshire/decode (:body %) true) [:paths (keyword path)])]
    (nibblonian-post query body f "exists")))

(defn stat
  "Obtains file status information for a path."
  [path]
  (when (exists? path)
    (let [query {}
          body  {:paths [path]}
          f     #(get-in (cheshire/decode (:body %) true) [:paths (keyword path)])]
      (nibblonian-post query body f "stat"))))

(defn get-or-create-dir
  "Returns the path argument if the path exists and refers to a directory.  If
   the path exists and refers to a regular file then nil is returned.
   Otherwise, a new directory is created and the path is returned."
  [path]
  (log/debug "getting or creating dir: path =" path)
  (let [stats (stat path)]
    (cond (nil? stats)            (create path)
          (= (:type stats) "dir") path
          :else                   nil)))

(defn gen-output-dir
  "Either obtains or creates a default output directory using a specified base name."
  [base]
  (first
   (filter #(not (nil? (get-or-create-dir %)))
           (cons base (map #(str base "-" %) (iterate inc 1))))))

(defn build-path
  "Builds a path from a base path and a list of components."
  [path & components]
  (string/join
   "/"
   (cons (string/replace path #"/+$" "")
         (map #(string/replace % #"^/+|/+$" "") components))))
