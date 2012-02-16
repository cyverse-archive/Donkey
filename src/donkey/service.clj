(ns donkey.service
  (:use [clojure.data.json :only (json-str)]
        [clojure.string :only (join)])
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]))

(def json-content-type "application/json")

(defn empty-response []
  {:status 200})

(defn success-response [map]
  {:status 200
   :body (json-str (merge {:success true} map))
   :content-type json-content-type})

(defn failure-response [e]
  (log/error e "internal error")
  {:status 400
   :body (json-str {:success false :reason (.getMessage e)})
   :content-type json-content-type})

(defn error-response [e]
  (log/error e "bad request")
  {:status 500
   :body (json-str {:success false :reason (.getMessage e)})
   :content-type json-content-type})

(defn unrecognized-path-response []
  "Builds the response to send for an unrecognized service path."
  (let [msg "unrecognized service path"]
    (json-str {:success false :reason msg})))

(defn build-url
  "Builds a URL from a base URL and one or more URL components."
  [base & components]
  (join "/" (map #(.replaceAll % "^/|/$" "")
                 (cons base components))))

(defn forward-get
  "Forwards a GET request to a remote service."
  [url request]
  (client/get url {:headers (:headers request)}))

(defn forward-post
  "Forwards a POST request to a remote service."
  [url request body]
  (client/post url {:headers (:headers request)
                    :body body}))

(defn forward-put
  "Forwards a PUT request to a remote service."
  [url request body]
  (client/put url {:headers (:headers request)
                   :body body}))

(defn forward-delete
  "Forwards a DELETE request to a remote service."
  [url request]
  (client/delete url {:headers (:headers request)}))
