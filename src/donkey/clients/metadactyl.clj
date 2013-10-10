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

(defn get-app
  [app-id]
  (-> (client/get (secured-url "app" app-id)
                  {:query-params (secured-params)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn admin-list-tool-requests
  [params]
  (-> (client/get (unsecured-url "tool-requests")
                  {:query-params (secured-params params)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn app-publishable?
  [app-id]
  (-> (client/get (secured-url "is-publishable" app-id)
                  {:query-params (secured-params)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn get-deployed-components-in-app
  [app-id]
  (-> (client/get (secured-url "get-components-in-analysis" app-id)
                  {:query-params (secured-params)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn submit-job
  [workspace-id submission]
  (-> (client/put (secured-url "workspaces" workspace-id "newexperiment")
                  {:query-params (secured-params)
                   :as           :stream})
      (:body)
      (service/decode-json)))
