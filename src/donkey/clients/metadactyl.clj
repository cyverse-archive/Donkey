(ns donkey.clients.metadactyl
  (:require [cemerick.url :as curl]
            [clj-http.client :as client]
            [donkey.util.config :as config]
            [donkey.util.service :as service]
            [donkey.util.transformers :as xforms]))

(defn- secured-params
  ([]
     (secured-params {}))
  ([existing-params]
     (xforms/add-current-user-to-map existing-params)))

(defn- unsecured-url
  [& components]
  (str (apply curl/url (config/metadactyl-unprotected-base-url) components)))

(defn- secured-url
  [& components]
  (str (apply curl/url (config/metadactyl-base-url) components)))

(defn get-only-app-groups
  []
  (-> (client/get (secured-url "app-groups")
                  {:query-params (secured-params)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn apps-in-group
  [group-id]
  (-> (client/get (secured-url "get-analyses-in-group" group-id)
                  {:query-params (secured-params)
                   :as           :stream})
      (:body)
      (service/decode-json)))
