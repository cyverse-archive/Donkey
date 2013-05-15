(ns donkey.services.user-prefs
  (:use [slingshot.slingshot :only [try+ throw+]]
        [clojure-commons.error-codes]
        [donkey.clients.nibblonian]
        [donkey.util.config]
        [donkey.util.service]
        [donkey.auth.user-attributes])
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as cl]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.file-utils :as ft]))

(def default-output-dir-key :defaultOutputFolder)

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

(defn- add-default-output-dir
  "Adds the default output directory to a set of user preferences."
  [prefs path]
  (assoc prefs
    default-output-dir-key path))

(defn- extract-default-output-dir
  "Gets the default output directory from a set of user preferences."
  [prefs]
  (default-output-dir-key prefs))

(defn- generate-default-output-dir
  "Generates a default output directory for the user and stores it in the preferences."
  [prefs]
  (let [base  (build-path (home-dir) (default-output-dir))
        prefs (add-default-output-dir prefs (gen-output-dir base))]
    (settings riak-prefs-bucket (cheshire/encode prefs))
    prefs))

(defn user-prefs
  "Retrieves or saves the user's preferences."
  ([]
     (let [prefs      (cheshire/decode (settings riak-prefs-bucket) true)
           output-dir (extract-default-output-dir prefs)]
       (cheshire/encode
        (if (string/blank? output-dir)
          (generate-default-output-dir prefs)
          prefs))))
  ([prefs]
     (settings riak-prefs-bucket prefs)))

(def remove-prefs (partial remove-settings riak-prefs-bucket))
(def search-history (partial settings riak-search-hist-bucket))
(def clear-search-history (partial remove-settings riak-search-hist-bucket))

(defn save-default-output-dir
  "Saves the path to the user's default output folder in the user's preferences."
  [path]
  (-> (user-prefs)
      (cheshire/decode true)
      (add-default-output-dir path)
      (cheshire/encode)
      (user-prefs)))

(defn get-default-output-dir
  "Gets the path to the user's default output folder from the user's preferences."
  []
  (extract-default-output-dir (cheshire/decode (user-prefs) true)))
