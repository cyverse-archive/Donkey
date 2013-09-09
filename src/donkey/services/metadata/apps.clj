(ns donkey.services.metadata.apps
  (:use [donkey.auth.user-attributes :only [current-user]])
  (:require [donkey.clients.metadactyl :as metadactyl]
            [donkey.util.config :as config]
            [donkey.util.service :as service]
            [mescal.de :as agave]))

(defprotocol AppLister
  "Used to list apps available to the Discovery Environment."
  (listAppGroups [this])
  (listApps [this group-id]))

(deftype DeOnlyAppLister []
  AppLister
  (listAppGroups [this]
    (metadactyl/get-only-app-groups))
  (listApps [this group-id]
    (metadactyl/apps-in-group group-id)))

(deftype DeHpcAppLister [agave-client]
  AppLister
  (listAppGroups [this]
    (-> (metadactyl/get-only-app-groups)
        (update-in [:groups] conj (.publicAppGroup agave-client))))
  (listApps [this group-id]
    (if (= group-id (:id (.publicAppGroup agave-client)))
      (.listPublicApps agave-client)
      (metadactyl/apps-in-group group-id))))

(defn- get-app-lister
  []
  (if (config/agave-enabled)
    (DeHpcAppLister. (agave/de-agave-client-v1
                      (config/agave-base-url)
                      (config/agave-user)
                      (config/agave-pass)
                      (:shortUsername current-user)
                      (config/agave-jobs-enabled)))
    (DeOnlyAppLister.)))

(defn get-only-app-groups
  []
  (service/success-response (.listAppGroups (get-app-lister))))

(defn apps-in-group
  [group-id]
  (service/success-response (.listApps (get-app-lister) group-id)))
