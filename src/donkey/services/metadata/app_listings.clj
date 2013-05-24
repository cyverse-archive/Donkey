(ns donkey.services.metadata.app-listings
  (:require [donkey.clients.agave :as agave]
            [donkey.clients.metadactyl :as metadactyl]
            [donkey.util.config :as config]
            [donkey.util.service :as service]))

(def ^:private hpc-group-description "Apps that run on HPC resources.")
(def ^:private hpc-group-name "High-Performance Computing")
(def ^:private hpc-group-id "HPC")
(def ^:private unknown-value "UNKNOWN")

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
      (add-hpc-group)
      (service/success-response)))

(defn- format-hpc-app-listing
  [listing]
  (-> listing
      (dissoc :inputs :modules :ontolog :outputs :parallelism :parameters :tags :templatePath
              :testPath)
      (assoc
          :can_run              true
          :deleted              false
          :description          (:shortDescription listing)
          :disabled             (not (and (config/agave-jobs-enabled) (:available listing)))
          :edited_date          (System/currentTimeMillis)
          :group_id             hpc-group-id
          :group_name           hpc-group-name
          :integration_date     (System/currentTimeMillis)
          :integrator_email     unknown-value
          :integrator_name      unknown-value
          :is_favorite          false
          :is_public            (:public listing)
          :pipeline_eligibility {:is_valid false :reason "HPC App"}
          :rating               {:average 0.0}
          :step-count           1
          :wiki_url             "")
      (dissoc :available :checkpointable :deploymentPath :executionHost :executionType
              :helpURI :longDescription :public :revision :shortDescription :version)))

(defn apps-in-group
  [group-id]
  (service/success-response
   (if (= group-id hpc-group-id)
     (assoc (hpc-group) :templates (map format-hpc-app-listing (agave/list-apps)))
     (metadactyl/apps-in-group group-id))))
