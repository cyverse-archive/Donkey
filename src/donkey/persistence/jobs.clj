(ns donkey.persistence.jobs
  "Functions for storing and retrieving information about jobs that the DE has
   submitted to any excecution service."
  (:use [clojure-commons.core :only [remove-nil-values]]
        [kameleon.queries :only [get-user-id]]
        [korma.core]
        [slingshot.slingshot :only [throw+]])
  (:require [clojure-commons.error-codes :as ce]))

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
  [job-id job-name job-type username status & {:keys [app-name start-date end-date]}]
  (let [job-type-id (get-job-type-id job-type)
        user-id     (get-user-id username)]
    (insert :jobs
            (values (remove-nil-values
                     {:external_id (str job-id)
                      :job_name    job-name
                      :app_name    app-name
                      :start_date  start-date
                      :end_date    end-date
                      :job_type_id job-type-id
                      :user_id     user-id})))))
