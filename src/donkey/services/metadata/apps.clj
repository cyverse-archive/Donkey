(ns donkey.services.metadata.apps
  (:use [donkey.auth.user-attributes :only [current-user]]
        [donkey.persistence.jobs :only [save-job count-jobs get-jobs]]
        [donkey.util.validators :only [validate-map]]
        [korma.db :only [transaction]]
        [slingshot.slingshot :only [throw+]])
  (:require [cheshire.core :as cheshire]
            [clojure-commons.error-codes :as ce]
            [donkey.clients.metadactyl :as metadactyl]
            [donkey.clients.osm :as osm]
            [donkey.util.config :as config]
            [donkey.util.db :as db]
            [donkey.util.service :as service]
            [mescal.de :as agave]))

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
  {:name       string?
   :software   string?
   :id         number?
   :submitTime number?
   :status     string?})

(defn- store-agave-job
  ([agave job]
     (store-agave-job agave job nil))
  ([agave job app-name]
     (validate-map job agave-job-validation-map)
     (let [status (.translateJobStatus agave (:status job))]
       (save-job (:id job) (:name job) agave-job-type (:username current-user) status
                 :app-name   app-name
                 :start-date (db/timestamp-from-millis (:submitTime job))
                 :end-date   (db/timestamp-from-millis (:endTime job))))))

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

(defn- format-de-job
  [job state]
  (assoc job
    :startdate        (str (db/millis-from-timestamp (:startdate job)))
    :enddate          (str (db/millis-from-timestamp (:enddate job)))
    :analysis_id      (:analysis_id state)
    :analysis_details (:analysis_details state)
    :wiki_url         (:wiki_url state)
    :description      (:description state)
    :resultfolderid   (:output_dir state)))

(defn- list-de-jobs
  [limit offset sort-field sort-order]
  (let [username  (:username current-user)
        job-types [de-job-type]
        jobs      (get-jobs username limit offset sort-field sort-order job-types)
        ids       (map :id jobs)
        states    (into {} (map (juxt :uuid identity) (osm/get-jobs ids)))]
    (mapv #(format-de-job % (states (:id %))) jobs)))

(defn- list-all-jobs
  [agave limit offset sort-field sort-order]
  ;; TODO: implement me
  )

(defprotocol AppLister
  "Used to list apps available to the Discovery Environment."
  (listAppGroups [this])
  (listApps [this group-id])
  (getApp [this app-id])
  (getAppDeployedComponents [this app-id])
  (submitJob [this workspace-id submission])
  (listJobs [this limit offset sort-field sort-order])
  (populateJobsTable [this]))

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
    (metadactyl/submit-job workspace-id submission))
  (listJobs [this limit offset sort-field sort-order]
    (list-de-jobs limit offset sort-field sort-order))
  (populateJobsTable [this]
    (dorun (map store-de-job (osm/list-jobs)))))

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
      (metadactyl/submit-job workspace-id submission)
      (.submitJob agave-client submission)))
  (listJobs [this limit offset sort-field sort-order]
    (list-all-jobs agave-client limit offset sort-field sort-order))
  (populateJobsTable [this]
    (dorun (map store-de-job (osm/list-jobs)))))

(defn- get-app-lister
  []
  (if (config/agave-enabled)
    (DeHpcAppLister. (agave/de-agave-client-v1
                      (config/agave-base-url)
                      (config/agave-user)
                      (config/agave-pass)
                      (:shortUsername current-user)
                      (config/agave-jobs-enabled)
                      (config/irods-home)))
    (DeOnlyAppLister.)))

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

(defn- compare-number-strings
  [& args]
  (apply compare (map #(Long/parseLong %) args)))

(defn- base-comparator-for
  [sort-field]
  (if (contains? #{:startdate :enddate} sort-field)
    compare-number-strings
    compare))

(defn- illegal-sort-order
  [sort-order]
  (throw+ {:error_code ce/ERR_ILLEGAL_ARGUMENT
           :sort-order sort-order}))

(defn- comparator-for
  [sort-field sort-order]
  (condp contains? sort-order
    #{:desc :DESC} (comp - (base-comparator-for sort-field))
    #{:asc  :ASC}  (base-comparator-for sort-field)
                   (illegal-sort-order sort-order)))

(defn- sort-jobs
  [sort-field sort-order jobs]
  (let [compare-fn (comparator-for sort-field sort-order)]
    (sort-by sort-field compare-fn jobs)))

(defn- apply-limit
  [limit coll]
  (if-not (zero? limit)
    (take limit coll)
    coll))

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
    (.listJobs app-lister limit offset sort-field sort-order)))
