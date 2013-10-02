(ns donkey.services.metadata.apps
  (:use [donkey.auth.user-attributes :only [current-user]])
  (:require [donkey.clients.metadactyl :as metadactyl]
            [donkey.util.config :as config]
            [donkey.util.service :as service]
            [mescal.de :as agave]))

(def ^:private uuid-regexes
  [#"^\p{XDigit}{8}(?:-\p{XDigit}{4}){3}-\p{XDigit}{12}$"
   #"^[at]\p{XDigit}{32}"])

(defn- is-uuid?
  [id]
  (some #(re-find % id) uuid-regexes))

(defprotocol AppLister
  "Used to list apps available to the Discovery Environment."
  (listAppGroups [this])
  (listApps [this group-id])
  (getApp [this app-id])
  (getAppDeployedComponents [this app-id])
  (submitJob [this workspace-id submission]))

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
    (metadactyl/submit-job workspace-id submission)))

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
      (.submitJob agave-client submission))))

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

