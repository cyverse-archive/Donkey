(ns donkey.persistence.jobs
  "Functions for storing and retrieving information about jobs that the DE has
   submitted to any excecution service."
  (:use [clojure-commons.core :only [remove-nil-values]]
        [kameleon.queries :only [get-user-id]]
        [korma.core]
        [slingshot.slingshot :only [throw+]])
  (:require [clojure-commons.error-codes :as ce])
  (:import [java.util UUID]))

(defn- nil-if-zero
  "Returns nil if the argument value is zero."
  [v]
  (if (zero? v) nil v))

(defn- get-job-type-id
  "Fetches the primary key for the job type with the given name."
  [job-type]
  (let [id ((comp :id first) (select :job_types (where {:name job-type})))]
    (when (nil? id)
      (throw+ {:error_code ce/ERR_ILLEGAL_ARGUMENT
               :job-type   job-type}))
    id))

(defn save-job
  "Saves information about a job in the database."
  [job-id job-name job-type username status & {:keys [id app-name start-date end-date deleted]}]
  (let [job-type-id (get-job-type-id job-type)
        user-id     (get-user-id username)
        id          (or id (UUID/randomUUID))]
    (insert :jobs
            (values (remove-nil-values
                     {:id          id
                      :external_id (str job-id)
                      :job_name    job-name
                      :app_name    app-name
                      :start_date  start-date
                      :end_date    end-date
                      :status      status
                      :deleted     deleted
                      :job_type_id job-type-id
                      :user_id     user-id})))))

(defn- count-jobs-base
  "The base query for counting the number of jobs in the database for a user."
  [username]
  (-> (select* [:jobs :j])
      (join [:users :u] {:j.user_id :u.id})
      (aggregate (count :*) :count)
      (where {:u.username username})))

(defn count-all-jobs
  "Counts the total number of jobs in the database for a user."
  [username]
  ((comp :count first) (select (count-jobs-base username))))

(defn count-jobs
  "Counts the number of undeleted jobs in the database for a user."
  [username job-types]
  ((comp :count first)
   (select (count-jobs-base username)
           (join [:job_types :jt] {:j.job_type_id :jt.id})
           (where {:jt.name   [in job-types]
                   :j.deleted false}))))

(defn- translate-sort-field
  "Translates the sort field sent to get-jobs to a value that can be used in the query."
  [field]
  (case field
    :name          :j.job_name
    :analysis_name :j.app_name
    :startdate     :j.start_date
    :enddate       :j.end_date
    :status        :j.status))

(defn get-jobs
  "Gets a list of jobs satisfying a query."
  [username row-limit row-offset sort-field sort-order job-types]
  (select [:jobs :j]
          (join [:users :u] {:j.user_id :u.id})
          (join [:job_types :jt] {:j.job_type_id :jt.id})
          (fields [:j.external_id :id]
                  [:j.job_name    :name]
                  [:j.app_name    :analysis_name]
                  [:j.start_date  :startdate]
                  [:j.end_date    :enddate]
                  [:j.status      :status])
          (where {:j.deleted  false
                  :u.username username
                  :jt.name    [in job-types]})
          (order sort-field sort-order)
          (offset (nil-if-zero row-offset))
          (limit (nil-if-zero row-limit))))

(defn- add-job-type-clause
  "Adds a where clause for a set of job types if the set of job types provided is not nil
   or empty."
  [query job-types]
  (assert (or (nil? job-types) (sequential? job-types)))
  (if-not (empty? job-types)
    (where query {:jt.name [in job-types]})
    query))

(defn get-external-job-ids
  "Gets a list of external job identifiers satisfying a query."
  [username {:keys [job-types]}]
  (->> (-> (select* [:jobs :j])
           (join [:users :u] {:j.user_id :u.id})
           (join [:job_types :jt] {:j.job_type_id :jt.id})
           (fields [:j.external_id :id])
           (where {:u.username username})
           (add-job-type-clause job-types)
           (select))
       (map :id)))

(defn get-job-by-id
  "Gets a single job by its internal identifier."
  [id]
  (first
   (select [:jobs :j]
           (join [:users :u] {:j.user_id :u.id})
           (join [:job_types :jt] {:j.job_type_id :jt.id})
           (fields [:j.external_id :id]
                   [:j.job_name    :name]
                   [:j.app_name    :analysis_name]
                   [:j.start_date  :startdate]
                   [:j.end_date    :enddate]
                   [:j.status      :status]
                   [:jt.name       :job_type]
                   [:u.username    :username])
           (where {:j.id id}))))

(defn update-job
  "Updates an existing job in the database."
  ([id {:keys [status end-date deleted]}]
     (update :jobs
             (set-fields (remove-nil-values {:status   status
                                             :end_date end-date
                                             :deleted  deleted}))
             (where {:external_id id})))
  ([id status end-date]
     (update-job id {:status   status
                     :end-date end-date})))

(defn update-job-by-internal-id
  "Updates an existing job in the database using the internal identifier as the key."
  [id {:keys [status end-date deleted]}]
  (update :jobs
          (set-fields (remove-nil-values {:status   status
                                          :end_date end-date
                                          :deleted  deleted}))
          (where {:id id})))

(defn list-incomplete-jobs
  []
  (select [:jobs :j]
          (join [:users :u] {:j.user_id :u.id})
          (join [:job_types :jt] {:j.job_type_id :jt.id})
          (fields [:j.id          :id]
                  [:j.external_id :external_id]
                  [:j.status      :status]
                  [:u.username    :username]
                  [:jt.name       :job_type])
          (where {:j.deleted  false
                  :j.end_date nil})))

(defn list-jobs-to-delete
  [ids]
  (select [:jobs :j]
          (fields [:j.external_id :id]
                  [:j.deleted     :deleted])
          (where {:j.external_id [in ids]})))

(defn delete-jobs
  [ids]
  (update :jobs
          (set-fields {:deleted true})
          (where {:external_id [in ids]})))
