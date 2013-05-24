(ns donkey.services.metadata.app-listings
  (:require [donkey.clients.agave :as agave]
            [donkey.clients.metadactyl :as metadactyl]))

(defn get-only-app-groups
  []
  (metadactyl/get-only-app-groups))
