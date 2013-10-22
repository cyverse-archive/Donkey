(ns donkey.services.metadata.apps
  (:use [donkey.auth.user-attributes :only [current-user]]
        [donkey.persistence.jobs :only [save-job count-jobs get-jobs get-job-by-id update-job]]
        [donkey.util.validators :only [validate-map]]
        [korma.db :only [transaction]]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [cemerick.url :as curl]
            [cheshire.core :as cheshire]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.error-codes :as ce]
            [donkey.clients.metadactyl :as metadactyl]
            [donkey.clients.notifications :as dn]
            [donkey.clients.osm :as osm]
            [donkey.persistence.apps :as ap]
            [donkey.util.config :as config]
            [donkey.util.db :as db]
            [donkey.util.service :as service]
            [mescal.de :as agave])
  (:import [java.util UUID]))

(def ^:private de-job-type "DE")
(def ^:private agave-job-type "Agave")

(def ^:private uuid-regexes
  [#"^\p{XDigit}{8}(?:-\p{XDigit}{4}){3}-\p{XDigit}{12}$"
   #"^[at]\p{XDigit}{32}"])

(defn- is-uuid?
  [id]
  (some #(re-find % id) uuid-regexes))

(def ^:private agave-job-validation-map
  "The validation map to use for Agave jobs."
  {:name          string?
   :analysis_name string?
   :id            string?
   :startdate     string?
   :status        string?})

(defn- store-agave-job
  [agave id job]
  (println "DEBUG - storing agave job:" job)
  (validate-map job agave-job-validation-map)
  (let [status (.translateJobStatus agave (:status job))]
    (save-job (:id job) (:name job) agave-job-type (:username current-user) status
              :id         id
              :app-name   (:analysis_name job)
              :start-date (db/timestamp-from-str (str (:startdate job)))
              :end-date   (db/timestamp-from-str (str (:enddate job))))))

(defn- submit-agave-job
  [agave-client submission]
  (let [id     (UUID/randomUUID)
        cb-url (str (curl/url (config/agave-callback-base) (str id)))
        job    (.submitJob agave-client (assoc-in submission [:config :callbackUrl] cb-url))]
    (store-agave-job agave-client id job)))

(def ^:private de-job-validation-map
  "The validation map to use for DE jobs."
  {:name            string?
   :uuid            string?
   :analysis_id     string?
   :analysis_name   string?
   :submission_date #(or (string? %) (number? %))
   :status          string?})

(defn- get-end-date
  [{:keys [status completion_date now_date]}]
  (case status
    "Failed"    (db/timestamp-from-str now_date)
    "Completed" (db/timestamp-from-str completion_date)
                nil))

(defn- store-de-job
  [job]
  (validate-map job de-job-validation-map)
  (save-job (:uuid job) (:name job) de-job-type (:username current-user) (:status job)
            :app-name   (:analysis_name job)
            :start-date (db/timestamp-from-str (str (:submission_date job)))
            :end-date   (get-end-date job)
            :deleted    (:deleted job)))

(defn- store-submitted-de-job
  [job]
  (save-job (:id job) (:name job) de-job-type (:username current-user) (:status job)
            :app-name   (:analysis_name job)
            :start-date (db/timestamp-from-str (str (:startdate job)))))

(defn- format-de-job
  [states de-apps job]
  (let [state (states (:id job) {})
        app   (de-apps (:analysis_id state) {})]
   (assoc job
     :startdate        (str (or (db/millis-from-timestamp (:startdate job)) 0))
     :enddate          (str (or (db/millis-from-timestamp (:enddate job)) 0))
     :analysis_id      (:analysis_id state)
     :analysis_details (:description app)
     :wiki_url         (:wikiurl app "")
     :app_disabled     (:disabled app false)
     :description      (:description state)
     :resultfolderid   (:output_dir state))))

(defn- format-agave-job
  [job state]
  (assoc state
    :id            (:id job)
    :startdate     (str (or (db/millis-from-timestamp (:startdate job)) 0))
    :enddate       (str (or (db/millis-from-timestamp (:enddate job)) 0))
    :analysis_name (:analysis_name job)
    :status        (:status job)))

(defn- list-jobs-of-types
  [limit offset sort-field sort-order job-types]
  (get-jobs (:username current-user) limit offset sort-field sort-order job-types))

(defn- count-jobs-of-types
  [job-types]
  (count-jobs (:username current-user) job-types))

(defn- agave-job-id?
  [id]
  (re-matches #"\d+" id))

(defn- load-de-job-states
  [jobs]
  (let [de-jobs (remove (comp agave-job-id? :id) jobs)]
    (if-not (empty? de-jobs)
      (->> (osm/get-jobs (map :id de-jobs))
           (map (juxt :uuid identity))
           (into {}))
      {})))

(defn- load-agave-job-states
  [agave jobs]
  (clojure.pprint/pprint jobs)
  (let [agave-jobs (filter (comp agave-job-id? :id) jobs)]
    (if-not (empty? agave-jobs)
      (->> (.listJobs agave (map :id agave-jobs))
           (map (juxt :id identity))
           (into {}))
      {})))

(defn- load-app-details
  [ids]
  (into {} (map (juxt :id identity)
                (ap/load-app-details ids))))

(defn- list-de-jobs
  [limit offset sort-field sort-order]
  (let [jobs    (list-jobs-of-types limit offset sort-field sort-order [de-job-type])
        states  (load-de-job-states jobs)
        de-apps (load-app-details (map :analysis_id states))]
    (mapv (partial format-de-job states de-apps) jobs)))

(defn- format-job
  [de-states de-apps agave-states {:keys [id] :as job}]
  (if (agave-job-id? id)
    (format-agave-job job (agave-states id))
    (format-de-job de-states de-apps job)))

(defn- list-all-jobs
  [agave limit offset sort-field sort-order]
  (let [types        [de-job-type agave-job-type]
        jobs         (list-jobs-of-types limit offset sort-field sort-order types)
        de-states    (load-de-job-states jobs)
        de-apps      (load-app-details (map :analysis_id de-states))
        agave-states (load-agave-job-states agave jobs)]
    (map (partial format-job de-states de-apps agave-states) jobs)))

(defn- get-agave-job
  [agave id]
  (try+
   (not-empty (.listRawJob agave id))
   (catch [:status 404] _ (service/not-found "HPC job" id))
   (catch [:status 400] _ (service/not-found "HPC job" id))
   (catch Object _ (service/request-failure "lookup for HPC job" id))))

(defn- update-job-status
  ([id status end-date]
     (update-job id status (db/timestamp-from-str (str end-date))))
  ([agave id username prev-status]
     (let [job-info (get-agave-job agave id)]
       (service/assert-found job-info "HPC job" id)
       (when-not (= (:status job-info) prev-status)
         (update-job id (:status job-info) (db/timestamp-from-str (str (:enddate job-info))))
         (dn/send-agave-job-status-update username job-info)))))

(defprotocol AppLister
  "Used to list apps available to the Discovery Environment."
  (listAppGroups [this])
  (listApps [this group-id])
  (getApp [this app-id])
  (getAppDeployedComponents [this app-id])
  (submitJob [this workspace-id submission])
  (countJobs [this])
  (listJobs [this limit offset sort-field sort-order])
  (populateJobsTable [this])
  (updateJobStatus [this id username prev-status]))

(deftype DeOnlyAppLister []
  AppLister
  (listAppGroups [this]
    (metadactyl/get-only-app-groups))
  (listApps [this group-id]
    (metadactyl/apps-in-group group-id))
  (getApp [this app-id]
    (metadactyl/get-app app-id))
  (getAppDeployedComponents [this app-id]
    (metadactyl/get-deployed-components-in-app app-id))
  (submitJob [this workspace-id submission]
    (store-submitted-de-job (metadactyl/submit-job workspace-id submission)))
  (countJobs [this]
    (count-jobs-of-types [de-job-type]))
  (listJobs [this limit offset sort-field sort-order]
    (list-de-jobs limit offset sort-field sort-order))
  (populateJobsTable [this]
    (dorun (map store-de-job (osm/list-jobs))))
  (updateJobStatus [this id username prev-status]
    (throw+ {:error_code ce/ERR_BAD_REQUEST
             :reason     "HPC_JOBS_DISABLED"})))

(deftype DeHpcAppLister [agave-client]
  AppLister
  (listAppGroups [this]
    (-> (metadactyl/get-only-app-groups)
        (update-in [:groups] conj (.publicAppGroup agave-client))))
  (listApps [this group-id]
    (if (= group-id (:id (.publicAppGroup agave-client)))
      (.listPublicApps agave-client)
      (metadactyl/apps-in-group group-id)))
  (getApp [this app-id]
    (if (is-uuid? app-id)
      (metadactyl/get-app app-id)
      (.getApp agave-client app-id)))
  (getAppDeployedComponents [this app-id]
    (if (is-uuid? app-id)
      (metadactyl/get-deployed-components-in-app app-id)
      {:deployed_components [(.getAppDeployedComponent agave-client app-id)]}))
  (submitJob [this workspace-id submission]
    (if (is-uuid? (:analysis_id submission))
      (store-submitted-de-job (metadactyl/submit-job workspace-id submission))
      (submit-agave-job agave-client submission)))
  (countJobs [this]
    (count-jobs-of-types [de-job-type agave-job-type]))
  (listJobs [this limit offset sort-field sort-order]
    (list-all-jobs agave-client limit offset sort-field sort-order))
  (populateJobsTable [this]
    (dorun (map store-de-job (osm/list-jobs))))
  (updateJobStatus [this id username prev-status]
    (update-job-status agave-client id username prev-status)))

(defn- get-app-lister
  ([]
     (get-app-lister (:shortUsername current-user)))
  ([username]
     (if (config/agave-enabled)
       (DeHpcAppLister. (agave/de-agave-client-v1
                         (config/agave-base-url)
                         (config/agave-user)
                         (config/agave-pass)
                         username
                         (config/agave-jobs-enabled)
                         (config/irods-home)))
       (DeOnlyAppLister.))))

(defn- populate-jobs-table
  [app-lister]
  (let [username (:username current-user)]
    (transaction
     (when (zero? (count-jobs username))
       (.populateJobsTable app-lister)))))

(defn get-only-app-groups
  []
  (service/success-response (.listAppGroups (get-app-lister))))

(defn apps-in-group
  [group-id]
  (service/success-response (.listApps (get-app-lister) group-id)))

(defn get-app
  [app-id]
  (service/success-response (.getApp (get-app-lister) app-id)))

(defn get-deployed-components-in-app
  [app-id]
  (service/success-response (.getAppDeployedComponents (get-app-lister) app-id)))

(defn submit-job
  [workspace-id body]
  (service/success-response
   (.submitJob (get-app-lister) workspace-id (service/decode-json body))))

(defn list-jobs
  [workspace-id {:keys [limit offset sort-field sort-order]
                 :or   {limit      "0"
                        offset     "0"
                        sort-field :startdate
                        sort-order :desc}}]
  (let [limit      (Long/parseLong limit)
        offset     (Long/parseLong offset)
        sort-field (keyword sort-field)
        sort-order (keyword sort-order)
        app-lister (get-app-lister)]
    (populate-jobs-table app-lister)
    (service/success-response
     {:analyses  (.listJobs app-lister limit offset sort-field sort-order)
      :timestamp (str (System/currentTimeMillis))
      :total     (.countJobs app-lister)})))

(defn update-de-job-status
  [id status end-date]
  (update-job-status id status end-date))

(defn update-agave-job-status
  [uuid]
  (let [{:keys [id username status] :as job} (get-job-by-id (UUID/fromString uuid))
        username                             (string/replace (or username "") #"@.*" "")]
    (service/assert-found job "job" uuid)
    (service/assert-valid (= agave-job-type (:job_type job)) "job" uuid "is not an HPC job")
    (.updateJobStatus (get-app-lister username) id username status)))
