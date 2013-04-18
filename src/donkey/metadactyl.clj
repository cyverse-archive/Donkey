(ns donkey.metadactyl
  (:use [clojure.java.io :only [reader]]
        [donkey.config]
        [donkey.email]
        [donkey.service]
        [donkey.transformers]
        [donkey.user-attributes]
        [donkey.user-info :only [get-user-details]]
        [ring.util.codec :only [url-encode]])
  (:require [cheshire.core :as cheshire]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [donkey.notifications :as dn]))

(defn- build-metadactyl-secured-url
  "Adds the name and email of the currently authenticated user to the secured
   metadactyl URL with the given relative URL path."
  [& components]
  (apply build-url-with-query (metadactyl-base-url)
         (add-current-user-to-map {}) components))

(defn- secured-notification-url
  [req & components]
  (apply build-url-with-query (notificationagent-base-url)
         (add-current-user-to-map (:params req)) components))

(defn- build-metadactyl-secured-url-with-query
  "Adds the name and email of the currently authenticated user to the secured
   metadactyl URL with the given relative URL path."
  [query & components]
  (apply build-url-with-query (metadactyl-base-url)
         (add-current-user-to-map query) components))

(defn- build-metadactyl-unprotected-url
  "Builds the unsecured metadactyl URL from the given relative URL path."
  [& components]
  (apply build-url (metadactyl-unprotected-base-url) components))

(defn- build-unprotected-url-with-query
  "Builds an unsecured metadactyl URL from the given relative URL path.  Any
   query-string parameters that are present in the request will be forwarded
   to metadactyl."
  [request & components]
  (apply build-url-with-query (metadactyl-unprotected-base-url)
         (:params request) components))

(defn get-workflow-elements
  "A service to get information about workflow elements."
  [req element-type]
  (let [url (build-unprotected-url-with-query
              req "get-workflow-elements" element-type)]
    (forward-get url req)))

(defn search-deployed-components
  "A service to search information about deployed components."
  [req search-term]
  (let [url (build-metadactyl-unprotected-url
             "search-deployed-components" search-term)]
    (forward-get url req)))

(defn get-all-app-ids
  "A service to get the list of app identifiers."
  [req]
  (let [url (build-metadactyl-unprotected-url "get-all-analysis-ids")]
    (forward-get url req)))

(defn delete-categories
  "A service used to delete app categories."
  [req]
  (let [url (build-metadactyl-unprotected-url "delete-categories")]
    (forward-post url req)))

(defn validate-app-for-pipelines
  "A service used to determine whether or not an app can be included in a
   pipeline."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url
             "validate-analysis-for-pipelines" app-id)]
    (forward-get url req)))

(defn get-data-objects-for-app
  "A service used to list the data objects in an app."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url "analysis-data-objects" app-id)]
    (forward-get url req)))

(defn categorize-apps
  "A service used to recategorize apps."
  [req]
  (let [url (build-metadactyl-unprotected-url "categorize-analyses")]
    (forward-post url req)))

(defn get-app-categories
  "A service used to get a list of app categories."
  [req category-set]
  (let [url (build-metadactyl-unprotected-url
             "get-analysis-categories" category-set)]
    (forward-get url req)))

(defn can-export-app
  "A service used to determine whether or not an app can be exported to Tito."
  [req]
  (let [url (build-metadactyl-unprotected-url "can-export-analysis")]
    (forward-post url req)))

(defn add-app-to-group
  "A service used to add an existing app to an app group."
  [req]
  (let [url (build-metadactyl-unprotected-url "add-analysis-to-group")]
    (forward-post url req)))

(defn get-app
  "A service used to get an app in the format required by the DE."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url "get-analysis" app-id)]
    (forward-get url req)))

(defn get-app-details
  "A service used to get high-level details about an app."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url "analysis-details" app-id)]
    (forward-get url req)))

(defn get-app-secured
  "A secured service used to get an app in the format required by the DE."
  [req app-id]
  (let [url (build-metadactyl-secured-url "template" app-id)]
    (forward-get url req)))

(defn get-app-new-format
  "This service gets an app in the format required by the DE as of version 1.8."
  [req app-id]
  (forward-get
   (build-metadactyl-secured-url "template" app-id)
   req))

(defn get-only-analysis-groups
  "Retrieves the list of public analyses."
  [req workspace-id]
  (let [url (build-metadactyl-unprotected-url
             "get-only-analysis-groups" workspace-id)]
    (forward-get url req)))

(defn list-app
  "This service lists a single application.  The response body contains a JSON
   string representing an object containing a list of apps.  If an app with the
   provided identifier exists then the list will contain that app.  Otherwise,
   the list will be empty."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url "list-analysis" app-id)]
    (forward-get url req)))

(defn export-template
  "This service will export the template with the given identifier."
  [req template-id]
  (let [url (build-metadactyl-unprotected-url "export-template" template-id)]
    (forward-get url req)))

(defn export-app
  "This service will export the single-step app with the given identifier."
  [req app-id]
  (forward-get
   (build-metadactyl-unprotected-url "export-app" app-id)
   req))

(defn export-workflow
  "This service will export a workflow with the given identifier."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url "export-workflow" app-id)]
    (forward-get url req)))

(defn export-deployed-components
  "This service will export all or selected deployed components."
  [req]
  (let [url (build-metadactyl-unprotected-url "export-deployed-components")]
    (forward-post url req)))

(defn preview-template
  "This service will convert a JSON document in the format consumed by
   the import service into the format required by the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "preview-template")]
    (forward-post url req)))

(defn preview-workflow
  "This service will convert a JSON document in the format consumed by
   the import service into the format required by the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "preview-workflow")]
    (forward-post url req)))

(defn import-template
  "This service will import a template into the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "import-template")]
    (forward-post url req)))

(defn import-workflow
  "This service will import a workflow into the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "import-workflow")]
    (forward-post url req)))

(defn import-tools
  "This service will import deployed components into the DE and send
   notifications if notification information is included and the deployed
   components are successfully imported."
  [req]
  (let [json-string (slurp (:body req))
        json-obj    (cheshire/decode json-string true)
        url (build-metadactyl-unprotected-url "import-tools")]
    (forward-post url req json-string)
    (dorun (map #(dn/send-tool-notification %) (:components json-obj))))
  (success-response))

(defn update-app
  "This service will update the information at the top level of an analysis.
   It will not update any of the components of the analysis."
  [req]
  (let [url (build-metadactyl-unprotected-url "update-analysis")]
    (forward-post url req)))

(defn update-template
  "This service will either update an existing template or import a new template."
  [req]
  (let [url (build-metadactyl-unprotected-url "update-template")]
    (forward-post url req)))

(defn update-workflow
  "This service will either update an existing workflow or import a new workflow."
  [req]
  (let [url (build-metadactyl-unprotected-url "update-workflow")]
    (forward-post url req)))

(defn force-update-workflow
  "This service will either update an existing workflow or import a new workflow.
   Vetted workflows may be updated."
  [req]
  (let [url (build-unprotected-url-with-query req "force-update-workflow")]
    (forward-post url req)))

(defn update-app-labels
  "This service updates the labels in a single-step app. Both vetted and unvetted apps can be
   modified using this service."
  [req]
  (let [url (build-unprotected-url-with-query req "update-app-labels")]
    (forward-post url req)))

(defn delete-workflow
  "This service will logically remove a workflow from the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "delete-workflow")]
    (forward-post url req)))

(defn permanently-delete-workflow
  "This service will physically remove a workflow from the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "permanently-delete-workflow")]
    (forward-post url req)))

(defn bootstrap
  "This service obtains information about and initializes the workspace for
   the authenticated user."
  [req]
  (let [url (build-metadactyl-secured-url "bootstrap")]
    (forward-get url req)))

(defn get-messages
  "This service forwards requests to the notification agent in order to
   retrieve notifications that the user may or may not have seen yet."
  [req]
  (let [url (dn/notificationagent-url "messages" (:params req))]
    (dn/add-app-details (forward-get url req))))

(defn get-unseen-messages
  "This service forwards requests to the notification agent in order to
   retrieve notifications that the user hasn't seen yet."
  [req]
  (let [url (dn/notificationagent-url "unseen-messages")]
    (dn/add-app-details (forward-get url req))))

(defn last-ten-messages
  "This service forwards requests for the ten most recent notifications to the
   notification agent."
  [req]
  (let [url (dn/notificationagent-url "last-ten-messages" (:params req))]
    (dn/add-app-details (forward-get url req))))

(defn count-messages
  "This service forwards requests to the notification agent in order to
   retrieve the number of notifications satisfying the conditions in the
   query string."
  [req]
  (let [url (dn/notificationagent-url "count-messages" (:params req))]
    (forward-get url req)))

(defn delete-notifications
  "This service forwards requests to the notification agent in order to delete
   existing notifications."
  [req]
  (let [url (dn/notificationagent-url "delete")]
    (forward-post url req)))

(defn delete-all-notifications
  "This service forwards requests to the notification agent in order to delete
   all notifications for the user."
  [params]
  (let [url (dn/notificationagent-url "delete-all" params)]
    (forward-delete url params)))

(defn mark-notifications-as-seen
  "This service forwards requests to the notification agent in order to mark
   notifications as seen by the user."
  [req]
  (let [url (dn/notificationagent-url "seen")]
    (forward-post url req)))

(defn mark-all-notifications-seen
  "This service forwards requests to the notification agent in order to mark all
   notifications as seen for the user."
  [req]
  (let [url (dn/notificationagent-url "mark-all-seen")]
    (forward-post url req (cheshire/encode (add-current-user-to-map {})))))

(defn send-notification
  "This service forwards a notifiction to the notification agent's general
   notification endpoint."
  [req]
  (let [url (dn/notificationagent-url "notification")]
    (forward-post url req)))

(defn get-system-messages
  "This service forwards a notification to the notification agent's endpoint
   for retrieving system messages."
  [req]
  (forward-get (secured-notification-url req "system" "messages") req))

(defn get-unseen-system-messages
  "Forwards a request to the notification agent's endpoint for getting
   unseen system messages."
  [req]
  (forward-get (secured-notification-url req "system" "unseen-messages") req))

(defn mark-system-messages-seen
  "Forwards a request to the notification to mark a set of system notifications
   as seen."
  [req]
  (forward-post (secured-notification-url req "system" "seen") req))

(defn mark-all-system-messages-seen
  "Forwards a request to the notification-agent to mark all system notifications as seen."
  [req]
  (forward-post (secured-notification-url req "system" "mark-all-seen") req))

(defn delete-system-messages
  "Forwards a request to the notification-agent to soft-delete a set of system messages."
  [req]
  (forward-post (secured-notification-url req "system" "delete") req))

(defn delete-all-system-messages
  "Forwards a request to to the notification-agent to soft-delete all system messages for a
   set of users."
  [req]
  (forward-delete (secured-notification-url req "system" "delete-all") req))

(defn admin-add-system-message
  "Forwards a request to the notification-agent to allow an admin to add a new system
   message."
  [req]
  (forward-put (secured-notification-url req "admin" "system") req))

(defn admin-list-system-types
  "Forwards a request to the notification-agent to allow an admin to list the current
   list of system notification types."
  [req]
  (forward-get (secured-notification-url req "admin" "system-types") req))

(defn admin-get-system-message
  "Forwards a request to the notification-agent to get a system notification for an admin."
  [req uuid]
  (forward-get (secured-notification-url req "admin" "system" uuid) req))

(defn admin-update-system-message
  "Forwards a request to the notification-agent to update a system notification for an admin."
  [req uuid]
  (forward-post (secured-notification-url req "admin" "system" uuid) req))

(defn admin-delete-system-message
  "Forwards a request to the notification-agent to delete a system notification for an admin."
  [req uuid]
  (forward-delete (secured-notification-url req "admin" "system" uuid) req))

(defn run-experiment
  "This service accepts a job submission from a user then reformats it and
   submits it to the JEX."
  [req workspace-id]
  (let [url (build-metadactyl-secured-url
             "workspaces" workspace-id "newexperiment")]
    (forward-put url req)))

(defn get-experiments
  "This service retrieves information about jobs that a user has submitted."
  [req workspace-id]
  (let [url (build-metadactyl-secured-url-with-query
              (:params req)
              "workspaces" workspace-id "executions" "list")]
    (forward-get url req)))

(defn get-selected-experiments
  "This service retrieves information about selected jobs that a user has
   submitted."
  [req workspace-id]
  (let [url (build-metadactyl-secured-url-with-query
              (:params req)
              "workspaces" workspace-id "executions" "list")]
    (forward-post url req)))

(defn delete-experiments
  "This service marks experiments as deleted so that they no longer show up
   in the Analyses window."
  [req workspace-id]
  (let [url (build-metadactyl-secured-url
              "workspaces" workspace-id "executions" "delete")]
    (forward-put url req)))

(defn rate-app
  "This service adds a user's rating to an app."
  [req]
  (let [url (build-metadactyl-secured-url "rate-analysis")]
    (forward-post url req)))

(defn delete-rating
  "This service removes a user's rating from an app."
  [req]
  (let [url (build-metadactyl-secured-url "delete-rating")]
    (forward-post url req)))

(defn search-apps
  "This service searches for apps based on a search term."
  [req]
  (let [url (build-metadactyl-secured-url-with-query
              (:params req)
              "search-analyses")]
    (forward-get url req)))

(defn list-apps-in-group
  "This service lists all of the apps in an app group and all of its
   descendents."
  [req app-group-id]
  (let [url (build-metadactyl-secured-url-with-query
              (:params req)
              "get-analyses-in-group"
              app-group-id)]
    (forward-get url req)))

(defn list-deployed-components-in-app
  "This service lists all of the deployed components in an app."
  [req app-id]
  (let [url (build-metadactyl-secured-url "get-components-in-analysis" app-id)]
    (forward-get url req)))

(defn update-favorites
  "This service adds apps to or removes apps from a user's favorites list."
  [req]
  (let [url (build-metadactyl-secured-url "update-favorites")]
    (forward-post url req)))

(defn edit-app
  "This service makes an app available in Tito for editing."
  [req app-id]
  (let [url (build-metadactyl-secured-url "edit-template" app-id)]
    (forward-get url req)))

(defn edit-app-new-format
  "This service makes an app available in Tito for editing and returns a
   representation of the app in the JSON format required by the DE as of
   version 1.8."
  [req app-id]
  (forward-get
   (build-metadactyl-secured-url "edit-app" app-id)
   req))

(defn edit-workflow
  "This service makes a workflow available for editing in the client."
  [req app-id]
  (let [url (build-metadactyl-secured-url "edit-workflow" app-id)]
    (forward-get url req)))

(defn copy-app
  "This service makes a copy of an app available in Tito for editing."
  [req app-id]
  (let [url (build-metadactyl-secured-url "copy-template" app-id)]
    (forward-get url req)))

(defn copy-workflow
  "This service makes a copy of a workflow available for editing in the client."
  [req app-id]
  (let [url (build-metadactyl-secured-url "copy-workflow" app-id)]
    (forward-get url req)))

(defn update-template-secured
  "This service will import an app into or update an app in the DE."
  [req]
  (let [url (build-metadactyl-secured-url "update-template")]
    (forward-put url req)))

(defn update-app-secured
  "This service will import a single-step app into or update an existing app in the DE."
  [req]
  (let [url (build-metadactyl-secured-url "update-app")]
    (forward-put url req)))

(defn make-app-public
  "This service copies an app from a user's private workspace to the public
   workspace."
  [req]
  (let [url (build-metadactyl-secured-url "make-analysis-public")]
    (forward-post url req)))

(defn get-property-values
  "Gets the property values for a previously submitted job."
  [req job-id]
  (let [url (build-metadactyl-unprotected-url "get-property-values" job-id)]
    (forward-get url req)))

(defn get-app-rerun-info
  "Gets the information required to rerun a previously executed app."
  [req job-id]
  (forward-get
   (build-metadactyl-unprotected-url "analysis-rerun-info" job-id)
   req))

(defn- add-user-details
  "Adds user details to the results from a request to obtain a list of
   collaborators."
  [{:keys [users]}]
  {:users (map get-user-details (filter #(not (string/blank? %)) users))})

(defn get-collaborators
  "Gets the list of collaborators from metadactyl and retrieves detailed
   information from Trellis."
  [req]
  (let [url      (build-metadactyl-secured-url "collaborators")
        response (forward-get url req)
        status   (:status response)]
    (if-not (or (< status 200) (> status 299))
      (success-response (add-user-details (decode-stream (:body response))))
      response)))

(defn- extract-usernames
  "Extracts the usernames from the request body for the services to add and
   remove collaborators."
  [{:keys [users]}]
  {:users (map :username users)})

(defn add-collaborators
  "Adds users to the list of collaborators for the current user."
  [req]
  (let [url   (build-metadactyl-secured-url "collaborators")
        users (cheshire/encode (extract-usernames (decode-stream (:body req))))]
    (forward-post url req users)))

(defn remove-collaborators
  "Adds users to the list of collaborators for the current user."
  [req]
  (let [url   (build-metadactyl-secured-url "remove-collaborators")
        users (cheshire/encode (extract-usernames (decode-stream (:body req))))]
    (forward-post url req users)))

(defn list-reference-genomes
  "Lists the reference genomes in the database."
  [req]
  (let [url (build-metadactyl-secured-url "reference-genomes")]
    (forward-get url req)))

(defn replace-reference-genomes
  "Replaces the reference genomes in the database with a new set of reference
   genomes."
  [req]
  (let [url (build-metadactyl-secured-url "reference-genomes")]
    (forward-put url req)))

(defn- postprocess-tool-request
  "Postprocesses a tool request update or submission. The postprocessing function
   should take the tool request and user details as arguments."
  [res f]
  (if (<= 200 (:status res) 299)
    (let [tool-req     (cheshire/decode-stream (reader (:body res)) true)
          username     (string/replace (:submitted_by tool-req) #"@.*" "")
          user-details (get-user-details username)]
      (f tool-req user-details))
    res))

(defn submit-tool-request
  "Submits a tool request on behalf of the authenticated user."
  [req]
  (postprocess-tool-request
   (forward-put (build-metadactyl-secured-url "tool-request") req)
   (fn [tool-req user-details]
     (send-tool-request-email tool-req user-details)
     (dn/send-tool-request-notification tool-req user-details)
     (success-response tool-req))))

(defn list-tool-requests
  "Lists the tool requests that were submitted by the authenticated user."
  [req]
  (forward-get
   (build-metadactyl-secured-url-with-query (:params req) "tool-requests")
   req))

(defn update-tool-request
  "Updates a tool request with comments and possibly a new status."
  [req]
  (postprocess-tool-request
   (forward-post (build-metadactyl-unprotected-url "tool-request") req)
   (fn [tool-req user-details]
     (dn/send-tool-request-update-notification tool-req user-details)
     (success-response tool-req))))

(defn update-tool-request-secured
  "Updates a tool request on behalf of the authenticated user."
  [req]
  (forward-post
   (build-metadactyl-secured-url "tool-request")
   req))

(defn get-tool-request
  "Lists details about a specific tool request."
  [req uuid]
  (forward-get
   (build-metadactyl-unprotected-url "tool-request" uuid)
   req))
