(ns donkey.file-listing
  (:use [clojure.data.json :only [json-str read-json]]
        [donkey.config]
        [donkey.service :only [build-url-with-query success-response]]
        [donkey.transformers :only [add-current-user-to-map]]
        [slingshot.slingshot :only [throw+]])
  (:require [clj-http.client :as client]
            [clojure.string :as string]))

(defn- build-path
  "Builds a path from a base path and a list of components."
  [path & components]
  (string/join
   "/"
   (cons (string/replace path #"/+$" "")
         (map #(string/replace % #"^/+|/+$" "") components))))

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
        res (client/post url {:body             (json-str body-map)
                              :content-type     :json
                              :throw-exceptions false})]
    (handle-nibblonian-resp res f)))

(defn- home-dir
  "Determines the home folder for the current user."
  []
  (nibblonian-get {} #(:body %) "home"))

(defn- exists
  "Determines whether or not a path exists."
  [path]
  (let [query {}
        body  {:paths [path]}
        f     #(get (:paths (read-json (:body %))) (keyword path))]
    (nibblonian-post query body f "exists")))

(defn- create
  "Creates a directory."
  [path]
  (let [query {}
        body  {:path path}
        f     #(:path (read-json (:body %)))]
    (nibblonian-post query body f "directory" "create")))

(defn get-default-output-dir
  "Determines whether or not the default directory name exists for a user."
  [dirname]
  (let [home (home-dir)
        path (build-path home dirname)]
    (if (exists path)
      (success-response {:path path})
      (success-response {:path (create path)}))))
