(ns donkey.service
  (:use [clojure.data.json :only (json-str)]
        [clojure.string :only (join)])
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]))

(def json-content-type "application/json")

(defn empty-response []
  {:status 200})

(defn success-response
  ([]
    (success-response {}))
  ([map]
    {:status 200
     :body (json-str (merge {:success true} map))
     :content-type json-content-type}))

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

(defn prepare-forwarded-request
  "Prepares a request to be forwarded to a remote service."
  ([request body]
  {:content-type (get-in request [:headers :content-type])
   :headers (dissoc (:headers request) "content-length" "content-type")
   :body body})

  ([request] (prepare-forwarded-request request nil)))

(defn forward-get
  "Forwards a GET request to a remote service."
  [url request]
  (client/get url (prepare-forwarded-request request)))

(defn forward-post
  "Forwards a POST request to a remote service."
  ([url request]
    (forward-post url request (slurp (:body request))))
  ([url request body]
    (client/post url (prepare-forwarded-request request body))))

(defn forward-put
  "Forwards a PUT request to a remote service."
  ([url request]
    (forward-put url request (slurp (:body request))))
  ([url request body]
    (client/put url (prepare-forwarded-request request body))))

(defn forward-delete
  "Forwards a DELETE request to a remote service."
  [url request]
  (client/delete url (prepare-forwarded-request request)))
