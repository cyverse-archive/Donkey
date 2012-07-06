(ns donkey.file-listing
  (:use [clojure.data.json :only [json-str read-json]]
        [donkey.config]
        [donkey.service :only [build-url-with-query success-response]]
        [donkey.transformers :only [add-current-user-to-map]]
        [donkey.user-prefs :only [user-prefs]]
        [slingshot.slingshot :only [throw+]])
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

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

(defn- create
  "Creates a directory."
  [path]
  (let [query {}
        body  {:path path}
        f     #(:path (read-json (:body %)))]
    (nibblonian-post query body f "directory" "create")))

(defn- stat
  "Obtains file status information for a path."
  [path]
  (let [query {}
        body  {:paths [path]}
        f     #(get (:paths (read-json (:body %))) (keyword path))]
    (nibblonian-post query body f "stat")))

(defn- save-default-output-dir
  "Saves the path to the user's default output folder in the user's prefs."
  [path]
  (user-prefs
   (json-str (assoc (read-json (user-prefs))
               :defaultOutputFolder path))))

(defn- get-or-create-dir
  "Returns the path argument if the path exists and refers to a directory.  If
   the path exists and refers to a regular file then nil is returned.
   Otherwise, a new directory is created and the path is returned."
  [path]
  (log/debug "getting or creating dir: path =" path)
  (let [stats (stat path)]
    (cond (nil? stats)            (create path)
          (= (:type stats) "dir") path
          :else                   nil)))

(defn- generate-output-dir
  "Automatically generates the default output directory based on the default
   name sent to the service."
  [base]
  (log/debug "generating output directory: base =" base)
  (let [home (home-dir)
        path (first
              (filter #(not (nil? (get-or-create-dir %)))
                      (cons base (map #(str base "-" %) (iterate inc 1)))))]
    (save-default-output-dir path)
    path))

(defn- validate-output-dir
  "Validates the user's selected output directory."
  [path]
  (log/debug "validating path:" path)
  (let [validated-path (get-or-create-dir path)]
    (when-not validated-path
      (throw+ {:type :regular-file-selected-as-output-folder
               :path  path}))
    path))

(defn get-default-output-dir
  "Determines whether or not the default directory name exists for a user."
  [dirname]
  (let [prefs (read-json (user-prefs))
        path  (:defaultOutputFolder prefs)]
    (if path
      (success-response {:path (validate-output-dir path)})
      (let [base  (build-path (home-dir) dirname)]
        (success-response {:path (generate-output-dir base)})))))
