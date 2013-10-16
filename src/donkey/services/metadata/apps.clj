(ns donkey.services.metadata.apps
  (:use [donkey.auth.user-attributes :only [current-user]]
        [donkey.persistence.jobs :only [save-job]]
        [donkey.util.validators :only [validate-map]]
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
  {:name          string?
   :id            string?
   :analysis_id   string?
   :analysis_name string?
   :startdate     string?
   :status        string?})

(defn- store-de-job
  [job]
  (validate-map job de-job-validation-map)
  (save-job (:id job) (:name job) de-job-type (:username current-user) (:status job)
            :app-name   (:analysis_name job)
            :start-date (db/timestamp-from-millis-str (:startdate job))
            :end-date   (db/timestamp-from-millis-str (:enddate job))))

(defprotocol AppLister
  "Used to list apps available to the Discovery Environment."
  (listAppGroups [this])
  (listApps [this group-id])
  (getApp [this app-id])
  (getAppDeployedComponents [this app-id])
  (submitJob [this workspace-id submission])
  (listJobs [this workspace-id params])
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
  (listJobs [this workspace-id params]
    (metadactyl/list-jobs this workspace-id params))
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
  (listJobs [this workspace-id params]
    (concat (metadactyl/list-jobs workspace-id params) (.listJobs agave-client)))
  (populateJobsTable [this]
    (dorun (map store-de-job (osm/list-jobs)))
    (dorun (map #(store-agave-job agave-client % (:analysis_name %))
                (.listRawJobs agave-client)))))

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
                        sort-order :desc}
                 :as   params}]
  (let [limit      (Long/parseLong limit)
        offset     (Long/parseLong offset)
        sort-field (keyword sort-field)
        sort-order (keyword sort-order)]
    (service/success-response
     {:analyses (->> (.listJobs (get-app-lister) workspace-id params)
                     (sort-jobs sort-field sort-order)
                     (drop offset)
                     (apply-limit limit))})))
