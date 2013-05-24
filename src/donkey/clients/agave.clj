(ns donkey.clients.agave
  (:use [clojure.java.io :only [reader]])
  (:require [cemerick.url :as curl]
            [clj-http.client :as client]
            [donkey.util.config :as config]
            [donkey.util.service :as service]))

(defn- agave-url
  [& path-components]
  (str (apply curl/url (config/agave-base-url) path-components)))

(defn list-apps
  []
  (-> (client/get (agave-url "apps-v1" "apps" "list")
                  {:accept :json
                   :as     :stream})
      (:body)
      (service/decode-json)
      (:result)))
