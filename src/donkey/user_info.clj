(ns donkey.user-info
  (:use [cemerick.url :only [url]]
        [clojure.data.json :only [json-str read-json]]
        [clojure.string :only [split]]
        [donkey.config])
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]))

(defn- user-search-url
  "Builds a URL that can be used to perform a specific type of user search."
  [type search-string]
  (str (url (userinfo-base-url) "users" type search-string)))

(defn- search
  "Performs a user search and returns the results as a vector of maps."
  [type search-string start end]
  (let [res (client/get (user-search-url type search-string)
                        {:insecure? true
                         :throw-exceptions false
                         :headers {"range" (str "records=" start "-" end)}})
        status (:status res)]
    (when (not (#{200 206 404} status))
      (throw (Exception. (str "user info service returned status " status))))
    (assoc (read-json (:body res)) :truncated (= status 206))))

(def
  ^{:private true
    :doc "The list of functions to use in a generalized search."}
   search-fns [(partial search "username") (partial search "name")
               (partial search "email")])

(defn- remove-duplicates
  "Removes duplicate user records from the merged search results."
  [results]
  (vals (into {} (map #(vector (:id %) %) results))))

(defn- to-int
  "Converts a string to an integer, throwing an IllegalArgumentException if
   the number can't be parsed.  This function is intended to be used from
   within parse-range."
  [string]
  (try
    (Integer/parseInt string)
    (catch NumberFormatException e
      (throw (IllegalArgumentException.
               (str "invalid number format in Range header: " string) e)))))

(defn parse-range
  "Parses the value of a range header in the request.  We expect the header
   value to be in the format, records=<first>-<last>.  For example, to get
   records 0 through 50, the header value should be records=0-50."
  [value]
  (if (nil? value)
    [0 (default-user-search-result-limit)]
    (let [[units begin-str end-str] (split value #"[=-]")
          [begin end] (map to-int [begin-str end-str])]
      (if (or (not= "records" units) (< begin 0) (< end 0) (>= begin end))
        (throw (IllegalArgumentException.
                 "invalid Range header value: should be records=0-50")))
      [begin end])))

(defn user-search
  "Performs user searches by username, name and e-mail address and returns the
   merged results."
  ([search-string]
    (user-search search-string 0 (default-user-search-result-limit)))
  ([search-string range-setting]
    (apply user-search search-string (parse-range range-setting)))
  ([search-string start end]
    (let [results (map #(% search-string start end) search-fns)
          users (remove-duplicates (apply concat (map #(:users %) results)))
          truncated (if (some :truncated results) true false)]
      (json-str {:users users :truncated truncated}))))

(defn empty-user-info
  "Returns an empty user-info record for the given username."
  [username]
  {:email     ""
   :firstname ""
   :id        -1
   :lastname  ""
   :username  username})

(defn get-user-details
  "Performs a user search for a single username."
  [username]
  (try
    (let [info (first (filter #(= (:username %) username)
                              (search "username" username 0 100)))]
      (when (nil? info)
        (log/warn (str "no user info found for username '" username "'")))
      (empty-user-info username))
    (catch Exception e
      (log/error e (str "username search for '" username "' failed"))
      (empty-user-info username))))
