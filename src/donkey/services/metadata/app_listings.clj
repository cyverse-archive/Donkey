(ns donkey.services.metadata.app-listings
  (:require [clojure.tools.logging :as log]
            [donkey.clients.agave :as agave]
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
   :template_count -1
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

(defn- agave-system-statuses
  []
  (when (config/agave-enabled)
    (let [system-listing (agave/list-systems)]
      (service/log-runtime
       ["extracting system statuses"]
       (into {} (map (fn [m] [(:resource.id m) (:status m)]) system-listing))))))

(defn- agave-app-enabled?
  [system-statuses listing]
  (and (config/agave-jobs-enabled)
       (:available listing)
       (= "up" (system-statuses (:executionHost listing)))))

(defn- format-hpc-app-listing
  [system-statuses listing]
  (-> listing
      (dissoc :available :checkpointable :deploymentPath :executionHost :executionType
              :helpURI :inputs :longDescription :modules :ontolog :outputs :parallelism
              :parameters :public :revision :shortDescription :tags :templatePath
              :testPath :version)
      (assoc
          :can_run              true
          :deleted              false
          :description          (:shortDescription listing)
          :disabled             (not (agave-app-enabled? system-statuses listing))
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
          :wiki_url             "")))

(defn apps-in-group
  [group-id]
  (let [system-statuses (agave-system-statuses)
        app-listing     (agave/list-apps)]
    (service/log-runtime
     ["formatting HPC app listing result"]
     (service/success-response
      (if (= group-id hpc-group-id)
        (assoc (hpc-group)
          :templates      (map (partial format-hpc-app-listing system-statuses) app-listing)
          :template_count (count app-listing))
        (metadactyl/apps-in-group group-id))))))
