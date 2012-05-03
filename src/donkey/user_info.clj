(ns donkey.user-info
  (:use [cemerick.url :only [url]]
        [clojure.data.json :only [json-str read-json]]
        [donkey.config])
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]))

(defn- user-search-url
  "Builds a URL that can be used to perform a specific type of user search."
  [type search-string]
  (str (url (userinfo-base-url) "users" type search-string)))

(defn- search
  "Performs a user search and returns the results as a vector of maps."
  [type search-string]
  (let [res (client/get (user-search-url type search-string)
                        {:insecure? true
                         :throw-exceptions false})
        status (:status res)]
    (when (not (#{200 404} status))
      (throw (Exception. (str "user info service returned status " status))))
    (read-json (:body res))))

(def
  ^{:private true
    :doc "The list of functions to use in a generalized search."}
   search-fns [(partial search "name") (partial search "email")])

(defn- remove-duplicate-users
  "Removes duplicate user records from the merged search results."
  [results]
  (vals (into {} (map #(vector (:id %) %) results))))

(defn user-search
  "Performs user searches by name and e-mail address and returns the merged
   results."
  [search-string]
  (let [results (map #(% search-string) search-fns)]
    (json-str {:users (remove-duplicate-users (apply concat results))})))
