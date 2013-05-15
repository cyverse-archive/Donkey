(ns donkey.util.service
  (:use [cemerick.url :only [url]]
        [ring.util.codec :only [url-encode]]
        [clojure.java.io :only [reader]]
        [clojure.string :only [join blank?]]
        [slingshot.slingshot :only [throw+]])
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojure-commons.error-codes :as ce])
  (:import [clojure.lang IPersistentMap]))

(defn empty-response []
  {:status 200})

(defn success-response
  ([]
     (success-response {}))
  ([map]
     {:status 200
      :body (cheshire/encode (merge {:success true} map))
      :content-type :json}))

(defn error-body [e]
  (cheshire/encode {:success false :reason (.getMessage e)}))

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

(defn invalid-arg-response [arg val reason]
  {:status       400
   :body         (cheshire/encode {:success false
                                   :code    "INVALID-ARGUMENT"
                                   :reason  reason
                                   :arg     (name arg)
                                   :val      val})
   :content-type :json})

(defn missing-arg-response [arg]
  (log/error "missing required argument:" (name arg))
  {:status       400
   :body         (cheshire/encode {:success false
                                   :code    "MISSING-REQUIRED-ARGUMENT"
                                   :arg     (name arg)})
   :content-type :json})

(defn temp-dir-failure-response [{:keys [parent prefix base]}]
  (log/error "unable to create a temporary directory in" parent
             "using base name" base)
  {:status       500
   :content-type :json
   :body         (cheshire/encode {:success    false
                                   :error_code "ERR-TEMP-DIR-CREATION"
                                   :parent     parent
                                   :prefix     prefix
                                   :base       base})})

(defn tree-file-parse-err-response [{:keys [details]}]
  {:status       400
   :content-type :json
   :body         (cheshire/encode {:success    false
                                   :error_code "ERR-TREE-FILE-PARSE"
                                   :details    details})})

(defn common-error-code [exception]
  (log/error ce/format-exception exception)
  (ce/err-resp (:object exception)))

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
    (cheshire/encode {:success false :reason msg})))

(defn build-url-with-query
  "Builds a URL from a base URL and one or more URL components.  Any query
   string parameters that are provided will be included in the result."
  [base query & components]
  (str (assoc (apply url base (map url-encode components)) :query query)))

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
  "Forwards a GET request to a remote service.  If no body is provided, the
   request body is stripped off.

   Parameters:
     addr - the URL receiving the request
     request - the request to send structured by compojure
     body - the body to attach to the request

   Returns:
     the response from the remote service"
  ([addr request]
     (client/get addr (prepare-forwarded-request request)))
  ([addr request body]
     (client/get addr (prepare-forwarded-request request body))))

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

(defn decode-stream
  "Decodes a stream containing a JSON object."
  [stream]
  (cheshire/decode-stream (reader stream) true))

(defn decode-json
  "Decodes JSON from either a string or an input stream."
  [source]
  (if (string? source)
    (cheshire/decode source true)
    (cheshire/decode-stream (reader source) true)))
