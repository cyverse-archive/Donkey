(ns donkey.clients.agave
  (:use [clojure.java.io :only [reader]])
  (:require [cemerick.url :as curl]
            [clj-http.client :as client]
            [donkey.util.config :as config]
            [donkey.util.service :as service]))

(defn- agave-url
  [& path-components]
  (str (apply curl/url (config/agave-base-url) path-components)))

(defn- get-app-listing
  []
  (-> (client/get (agave-url "apps-v1" "apps" "list")
                  {:accept :json
                   :as     :stream})
      (:body)
      (service/decode-json)
      (:result)))

(defn list-apps
  []
  (service/log-runtime ["obtaining app listing"] (get-app-listing)))

(defn count-apps
  []
  (service/log-runtime ["obtaining app count"] (count (list-apps))))

(defn list-systems
  []
  (service/log-runtime
   ["obtaining system list"]
   (-> (client/get (agave-url "apps-v1" "systems" "list"))
       (:body)
       (service/decode-json)
       (:result))))
