(ns donkey.services.callbacks
  "Service implementations for receiving callbacks from external services."
  (:use [donkey.util.service :only [decode-json]])
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]
            [donkey.services.metadata.apps :as apps]))

(defn- update-de-job-status
  [msg]
  (let [{:keys [id status enddate]} (:payload msg)]
    (apps/update-de-job-status id status enddate)))

(def ^:private notification-actions
  "Maps notification action codes to notifications."
  {:job_status_change update-de-job-status})

(defn receive-notification
  "Receives callbacks from the notification agent."
  [body]
  (let [msg       (decode-json body)
        action    (keyword (get-in msg [:payload :action]))
        action-fn (notification-actions action)]
    (when-not (nil? action-fn)
      (action-fn msg))))
