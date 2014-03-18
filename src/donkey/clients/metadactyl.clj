(ns donkey.clients.metadactyl
  (:require [cemerick.url :as curl]
            [cheshire.core :as cheshire]
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
  [group-id & [params]]
  (-> (client/get (secured-url "get-analyses-in-group" group-id)
                  {:query-params (secured-params params)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn search-apps
  [search-term]
  (-> (client/get (secured-url "search-analyses")
                  {:query-params (secured-params {:search search-term})
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
                  {:query-params params
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn list-tool-request-status-codes
  [params]
  (-> (client/get (unsecured-url "tool-request-status-codes")
                  {:query-params params
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

(defn get-app-details
  [app-id]
  (-> (client/get (unsecured-url "analysis-details" app-id)
                  {:query-params (secured-params)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn submit-job
  [workspace-id submission]
  (-> (client/put (secured-url "workspaces" workspace-id "newexperiment")
                  {:query-params (secured-params)
                   :content-type :json
                   :body         (cheshire/encode submission)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn get-property-values
  [job-id]
  (-> (client/get (unsecured-url "get-property-values" job-id)
                  {:query-params (secured-params)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn get-app-rerun-info
  [job-id]
  (-> (client/get (unsecured-url "app-rerun-info" job-id)
                  {:query-params (secured-params)
                   :as           :stream})
      (:body)
      (service/decode-json)))

(defn- update-favorites-request
  [app-id favorite?]
  {:analysis_id   app-id
   :user_favorite favorite?})

(defn update-favorites
  [app-id favorite?]
  (-> (client/post (secured-url "update-favorites")
                   {:query-params (secured-params)
                    :body         (cheshire/encode (update-favorites-request app-id favorite?))
                    :as           :stream})
      (:body)
      (service/decode-json)))

(defn- rate-app-request
  [app-id rating comment-id]
  {:analysis_id app-id
   :rating      rating
   :comment_id  comment-id})

(defn rate-app
  [app-id rating comment-id]
  (-> (client/post (secured-url "rate-analysis")
                   {:query-params (secured-params)
                    :body         (cheshire/encode (rate-app-request app-id rating comment-id))
                    :as           :stream})
      (:body)
      (service/decode-json)))

(defn delete-rating
  [app-id]
  (-> (client/post (secured-url "delete-rating")
                   {:query-params (secured-params)
                    :body         (cheshire/encode {:analysis_id app-id})
                    :as           :stream})
      (:body)
      (service/decode-json)))
