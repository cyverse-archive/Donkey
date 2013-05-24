(ns donkey.services.metadata.app-listings
  (:require [donkey.clients.agave :as agave]
            [donkey.clients.metadactyl :as metadactyl]
            [donkey.util.config :as config]
            [donkey.util.service :as service]))

(def ^:private hpc-group-description "Apps that run on HPC resources.")
(def ^:private hpc-group-name "High-Performance Computing")
(def ^:private hpc-group-id "HPC")

(defn- hpc-group
  []
  {:description    hpc-group-description
   :id             hpc-group-id
   :is_public      true
   :name           hpc-group-name
   :template_count (agave/count-apps)
   :workspace_id   0})

(defn- add-hpc-group
  [app-groups]
  (if (config/agave-enabled)
    (update-in app-groups [:groups] conj (hpc-group))
    app-groups))

(defn get-only-app-groups
  []
  (-> (metadactyl/get-only-app-groups)
      (:body)
      (service/decode-json)
      (add-hpc-group)
      (service/success-response)))
