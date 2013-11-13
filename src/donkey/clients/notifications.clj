(ns donkey.clients.notifications
  (:use [donkey.util.config :only
         [notificationagent-base-url metadactyl-unprotected-base-url]]
        [donkey.util.service :only [build-url build-url-with-query decode-stream]]
        [donkey.util.transformers :only [add-current-user-to-map]])
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- app-description-url
  "Builds a URL that can be used to fetch the description for the App with the
   given ID."
  [app-id]
  (build-url (metadactyl-unprotected-base-url) "get-app-description" app-id))

(defn- get-app-description
  "Gets an app description from the database."
  [app-id]
  (log/debug "looking up the description for app" app-id)
  (if (empty? app-id)
    ""
    (:body (client/get (app-description-url app-id)))))

(defn- add-app-details-to-message
  "Adds application details to a single message."
  [msg]
  (let [app-id (get-in msg [:payload :analysis_id])]
    (assoc-in msg [:payload :analysis-details]
              (get-app-description app-id))))

(defn- add-app-details-to-messages
  "Adds application details to a list of messages."
  [msgs]
  (map add-app-details-to-message msgs))

(defn- add-app-details-to-map
  "Adds application details to a map."
  [m]
  (update-in m [:messages] add-app-details-to-messages))

(defn add-app-details
  "Adds application details to notifications in a response from the
   notification agent."
  [res]
  (let [m (decode-stream (:body res))]
    (log/debug "adding app details to notifications:" m)
    (cheshire/encode (add-app-details-to-map m))))

(defn notificationagent-url
  "Builds a URL that can be used to connect to the notification agent."
  ([relative-url]
     (notificationagent-url relative-url {}))
  ([relative-url query]
     (build-url-with-query (notificationagent-base-url)
       (add-current-user-to-map query) relative-url)))

(defn send-notification
  "Sends a notification to a user."
  [m]
  (let [res (client/post (notificationagent-url "notification")
                         {:content-type :json
                          :body (cheshire/encode m)})]
    res))

(defn send-tool-notification
  "Sends notification of tool deployment to a user if notification information
   is included in the import JSON."
  [m]
  (let [{:keys [user email]} m]
    (when (every? (comp not nil?) [user email])
      (try
        (send-notification {:type "tool"
                            :user user
                            :subject (str (:name m) " has been deployed")
                            :email true
                            :email_template "tool_deployment"
                            :payload {:email_address email
                                      :toolname (:name m)
                                      :tooldirectory (:location m)
                                      :tooldescription (:description m)
                                      :toolattribution (:attribution m)
                                      :toolversion (:version m)}})
        (catch Exception e
          (log/warn e "unable to send tool deployment notification for" m))))))

(defn send-tool-request-notification
  "Sends notification of a successful tool request submission to the user."
  [tool-req user-details]
  (let [this-update (last (:history tool-req))
        comments    (:comments this-update)]
    (try
      (send-notification {:type           "tool_request"
                          :user           (:username user-details)
                          :subject        (str "Tool Request " (:name tool-req) " Submitted")
                          :email          true
                          :email_template "tool_request_submitted"
                          :payload        (assoc tool-req
                                                 :email_address (:email user-details)
                                                 :toolname      (:name tool-req)
                                                 :comments      comments)})
      (catch Exception e
        (log/warn e "unable to send tool request submission notification for" tool-req)))))

(defn send-tool-request-update-notification
  "Sends notification of a tool request status change to the user."
  [tool-req user-details]
  (let [this-update (last (:history tool-req))
        status      (:status this-update)
        comments    (:comments this-update)
        subject     (str "Tool Request " (:name tool-req) " Status Changed to " status)]
    (try
      (send-notification {:type           "tool_request"
                          :user           (:username user-details)
                          :subject        subject
                          :email          true
                          :email_template (str "tool_request_" (string/lower-case status))
                          :payload        (assoc tool-req
                                            :email_address (:email user-details)
                                            :toolname      (:name tool-req)
                                            :comments      comments)})
      (catch Exception e
        (log/warn e "unable to send tool request update notification for" tool-req)))))

(defn send-agave-job-status-update
  "Sends notification of an Agave job status update to the user."
  [username job-info]
  (try
    (send-notification
     {:type           "analysis"
      :user           username
      :subject        (str (:name job-info) " status changed.")
      :message        (str (:name job-info) " " (string/lower-case (:status job-info)))
      :email          (if (#{"Completed" "Failed"} (:status job-info)) true false)
      :email-template "analysis_status_change"
      :payload        (assoc job-info
                        :action "job_status_change"
                        :user   username)})
    (catch Exception e
      (log/warn e "unable to send job status update notification for" (:id job-info)))))
