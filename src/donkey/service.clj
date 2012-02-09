(ns donkey.service
  (:use [clojure.data.json :only (json-str)])
  (:require [clojure.tools.logging :as log]))

(def json-content-type "application/json")

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
