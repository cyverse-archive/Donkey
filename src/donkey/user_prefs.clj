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
  [bucket-fn user]
  (str
    (string/join "/"
      (map ft/rm-last-slash [(riak-base-url) (bucket-fn) user]))
    "?returnbody=true"))

(defn- request-failed
  "Throws an exception for a failed request."
  [resp]
  (throw+ {:error_code ERR_REQUEST_FAILED
           :body       (:body resp)}))

(defn- settings
  ([bucket-fn]
     (let [user (:username current-user)
           url  (key-url bucket-fn user)]
       (log/debug "settings: GET" url)
       (let [resp (cl/get url {:throw-exceptions false})]
         (cond
          (= 200 (:status resp)) (:body resp)
          (= 404 (:status resp)) "{}"
          :else                  (request-failed resp)))))

  ([bucket-fn new-settings]
     (let [user (:username current-user)
           url  (key-url bucket-fn user)]
       (log/debug "settings: POST" url new-settings)
       (let [resp (cl/post
                   url
                   {:content-type :json :body new-settings}
                   {:throw-exceptions false})]
         (cond
          (= 200 (:status resp)) (:body resp)
          :else                  (request-failed resp))))))

(defn- remove-settings
  "Removes saved user settings from the Riak server."
  [bucket-fn]
  (let [user   (:username current-user)
        url    (key-url bucket-fn user)
        _      (log/debug "settings: DELETE" url)
        resp   (cl/delete url {:throw-exceptions false})
        status (:status resp)]
    (cond (= 404 (:status resp))      (success-response)
          (<= 200 (:status resp) 299) (success-response)
          :else                       (request-failed resp))))

(def user-prefs (partial settings #(riak-prefs-bucket)))
(def remove-prefs (partial remove-settings #(riak-prefs-bucket)))
(def search-history (partial settings #(riak-search-hist-bucket)))
(def clear-search-history (partial remove-settings #(riak-search-hist-bucket)))
