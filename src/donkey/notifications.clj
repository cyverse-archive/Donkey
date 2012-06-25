(ns donkey.notifications
  (:use [clojure.data.json :only [json-str read-json]]
        [donkey.config :only
         [notificationagent-base-url metadactyl-unprotected-base-url]]
        [donkey.service :only [build-url build-url-with-query]]
        [donkey.transformers :only [add-current-user-to-map]])
  (:require [clj-http.client :as client]
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
  (map #(add-app-details-to-message %) msgs))

(defn- add-app-details-to-map
  "Adds application details to a map."
  [m]
  (assoc m :messages (add-app-details-to-messages (:messages m))))

(defn add-app-details
  "Adds application details to notifications in a response from the
   notification agent."
  [res]
  (let [m (read-json (slurp (:body res)))]
    (log/debug "adding app details to notifications:" m)
    (json-str (add-app-details-to-map m))))

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
  (client/post (notificationagent-url "notification")
               {:content-type :json
                :body (json-str m)}))

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
