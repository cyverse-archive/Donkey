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

(defn count-jobs
  "Counts the number of jobs in the database for a user."
  ([username]
     ((comp :count first)
      (select [:jobs :j]
              (join [:users :u] {:j.user_id :u.id})
              (aggregate (count :*) :count)
              (where {:u.username username}))))
  ([username job-types]
     ((comp :count first)
      (select [:jobs :j]
              (join [:users :u] {:j.user_id :u.id})
              (join [:job_types :jt] {:j.job_type_id :jt.id})
              (aggregate (count :*) :count)
              (where {:u.username username
                      :jt.name    [in job-types]})))))

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
  [id status end-date]
  (update :jobs
          (set-fields (remove-nil-values {:status   status
                                          :end_date end-date}))
          (where {:external_id id})))
