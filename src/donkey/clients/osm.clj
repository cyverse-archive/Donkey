(ns donkey.clients.osm
  (:use [donkey.auth.user-attributes :only [current-user]])
  (:require [clojure-commons.osm :as osm]
            [donkey.util.service :as service]
            [donkey.util.config :as config]))

(def ^:private osm-jobs-client
  (memoize (fn [] (osm/create (config/osm-base-url) (config/osm-jobs-bucket)))))

(defn list-jobs
  []
  (map :state
       ((comp :objects service/decode-json)
        (osm/query (osm-jobs-client) {:state.user (:shortUsername current-user)}))))

(defn get-jobs
  [ids]
  (map :state
       ((comp :objects service/decode-json)
        (osm/query (osm-jobs-client) {:state.uuid {"$in" ids}}))))
