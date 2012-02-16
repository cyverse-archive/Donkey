(ns donkey.transformers
  (:use [clojure.data.json :only (json-str read-json)])
  (:require [clojure.tools.logging :as log])
  (:import [net.sf.json JSONObject]))

(defn object->json
  "Converts a Java object to a JSON string."
  [obj]
  (.toString (JSONObject/fromObject obj)))

(defn add-username-to-json
  "Adds the name of the currently authenticated user to a JSON object in the
   body of a request, and returns only the updated body."
  [req]
  (let [m (read-json (slurp (:body req)))
        username (get-in req [:user-attributes :uid])]
    (json-str (assoc m :user username))))

(defn string->long
  "Converts a String to a long."
  [string]
  (try
    (Long/parseLong string)
    (catch NumberFormatException e
      (throw (IllegalArgumentException. e)))))
