(ns donkey.user-prefs
  (:use [slingshot.slingshot :only [try+ throw+]]
        [clojure-commons.error-codes]
        [donkey.config]
        [donkey.service]
        [donkey.user-attributes])
  (:require [clj-http.client :as cl]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.file-utils :as ft]))

(defn- key-url
  [user]
  (str 
    (string/join "/" 
      (map ft/rm-last-slash [(riak-base-url) (riak-prefs-bucket) user]))
    "?returnbody=true"))

(defn- request-failed
  "Throws an exception for a failed request."
  [resp]
  (throw+ {:error_code ERR_REQUEST_FAILED
           :body       (:body resp)}))

(defn user-prefs
  ([]
    (let [user (:username current-user)]
      (log/debug (str "user-prefs: GET " (key-url user)))
      (let [resp (cl/get (key-url user) {:throw-exceptions false})]
        (cond
          (= 200 (:status resp)) (:body resp)
          (= 404 (:status resp)) "{}"
          :else                  (request-failed resp)))))

  ([new-prefs]
    (let [user (:username current-user)]
      (log/debug (str "user-prefs: POST " (key-url user) " " new-prefs))
      (let [resp (cl/post 
                   (key-url user) 
                   {:content-type :json :body new-prefs} 
                   {:throw-exceptions false})]
        (cond
          (= 200 (:status resp)) (:body resp)
          :else                  (request-failed resp))))))

(defn remove-prefs
  "Removes user session information from the Riak cluster."
  []
  (let [user   (:username current-user)
        url    (key-url user)
        _      (log/debug "user-prefs: DELETE" url)
        resp   (cl/delete url {:throw-exceptions false})
        status (:status resp)]
    (cond (= 404 (:status resp))      (success-response)
          (<= 200 (:status resp) 299) (success-response)
          :else                       (request-failed resp))))
