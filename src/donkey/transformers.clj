(ns donkey.transformers
  (:use [cemerick.url :only [url]]
        [clojure.data.json :only [json-str read-json]]
        [donkey.user-attributes])
  (:require [clojure.tools.logging :as log])
  (:import [net.sf.json JSONObject]))

(defn object->json-str
  "Converts a Java object to a JSON string."
  [obj]
  (str (JSONObject/fromObject obj)))

(defn object->json-obj
  "Converts a Java object to a JSON object."
  [obj]
  (JSONObject/fromObject obj))

(defn add-username-to-json
  "Adds the name of the currently authenticated user to a JSON object in the
   body of a request, and returns only the updated body."
  [req]
  (let [m (read-json (slurp (:body req)))
        username (get-in req [:user-attributes "uid"])]
    (json-str (assoc m :user username))))

(defn add-current-user-to-map
  "Adds the name and e-mail address of the currently authenticated user to a
   map that can be used to generate a query string."
  [query]
  (assoc query
    :user  (:shortUsername current-user)
    :email (:email current-user)))

(defn add-current-user-to-url
  "Adds the name of the currently authenticated user to the query string of a
   URL."
  [addr]
  (let [url-map (url addr)
        query   (assoc (:query url-map)
                  :user  (:shortUsername current-user)
                  :email (:email current-user))
        url-map (assoc url-map :query query)]
    (str url-map)))
