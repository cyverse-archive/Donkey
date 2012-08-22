(ns donkey.service
  (:use [cemerick.url :only [url]]
        [clojure.data.json :only [json-str]]
        [clojure.string :only [join blank?]]
        [slingshot.slingshot :only [throw+]])
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log])
  (:import [clojure.lang IPersistentMap]))

(defn empty-response []
  {:status 200})

(defn success-response
  ([]
    (success-response {}))
  ([map]
    {:status 200
     :body (json-str (merge {:success true} map))
     :content-type :json}))

(defn error-body [e]
  (json-str {:success false :reason (.getMessage e)}))

(defn failure-response [e]
  (log/error e "bad request")
  {:status       400
   :body         (error-body e)
   :content-type :json})

(defn error-response [e]
  (log/error e "internal error")
  {:status       500
   :body         (error-body e)
   :content-type :json})

(defn missing-arg-response [arg]
  (log/error "missing required argument:" (name arg))
  {:status       400
   :body         (json-str {:success false
                            :code    "MISSING-REQUIRED-ARGUMENT"
                            :arg     (name arg)})
   :content-type :json})

(defn required-param
  "Retrieves a required parameter from a map.  The may may contain either query-
   string parameters or a map that has been generated from a JSON request body."
  [params k]
  (let [v (params k)]
    (when (blank? v)
      (throw+ {:type :missing-argument
               :arg  k}))
    v))

(defn unrecognized-path-response
  "Builds the response to send for an unrecognized service path."
  []
  (let [msg "unrecognized service path"]
    (json-str {:success false :reason msg})))

(defn build-url-with-query
  "Builds a URL from a base URL and one or more URL components.  Any query
   string parameters that are provided will be included in the result."
  [base query & components]
  (str (assoc (apply url base components) :query query)))

(defn build-url
  "Builds a URL from a base URL and one or more URL components."
  [base & components]
  (apply build-url-with-query base {} components))

(defn prepare-forwarded-request
  "Prepares a request to be forwarded to a remote service."
  ([request body]
     {:content-type (get-in request [:headers :content-type])
      :headers (dissoc
                (:headers request)
                "content-length"
                "content-type"
                "transfer-encoding")
      :body body
      :throw-exceptions false
      :as :stream})
  ([request]
     (prepare-forwarded-request request nil)))

(defn forward-get
  "Forwards a GET request to a remote service."
  [addr request]
  (client/get addr (prepare-forwarded-request request)))

(defn forward-post
  "Forwards a POST request to a remote service."
  ([addr request]
    (forward-post addr request (slurp (:body request))))
  ([addr request body]
    (client/post addr (prepare-forwarded-request request body))))

(defn forward-put
  "Forwards a PUT request to a remote service."
  ([addr request]
    (forward-put addr request (slurp (:body request))))
  ([addr request body]
    (client/put addr (prepare-forwarded-request request body))))

(defn forward-delete
  "Forwards a DELETE request to a remote service."
  [addr request]
  (client/delete addr (prepare-forwarded-request request)))
