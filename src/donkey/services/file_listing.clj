(ns donkey.services.file-listing
  (:use [donkey.clients.nibblonian]
        [donkey.services.user-prefs :only [user-prefs]]
        [donkey.util.config]
        [donkey.util.service :only [decode-stream required-param success-response]]
        [slingshot.slingshot :only [throw+]])
  (:require [cheshire.core :as cheshire]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- build-path
  "Builds a path from a base path and a list of components."
  [path & components]
  (string/join
   "/"
   (cons (string/replace path #"/+$" "")
         (map #(string/replace % #"^/+|/+$" "") components))))

(defn- save-default-output-dir
  "Saves the path to the user's default output folder in the user's prefs."
  [path]
  (user-prefs
   (cheshire/encode (assoc (cheshire/decode (user-prefs) true)
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
  (let [prefs (cheshire/decode (user-prefs) true)
        path  (:defaultOutputFolder prefs)]
    (if-not (string/blank? path)
      (success-response {:path (validate-output-dir path)})
      (let [base  (build-path (home-dir) dirname)]
        (success-response {:path (generate-output-dir base)})))))

(defn reset-default-output-dir
  "Resets the default output directory for a user."
  [body]
  (let [path (required-param (decode-stream body) :path)]
    (success-response
     {:path (generate-output-dir (build-path (home-dir) path))})))
