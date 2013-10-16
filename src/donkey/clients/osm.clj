(ns donkey.clients.osm
  (:use [donkey.auth.user-attributes :only [current-user]])
  (:require [cheshire.core :as cheshire]
            [clojure-commons.osm :as osm]
            [donkey.util.config :as config]))

(def ^:private osm-jobs-client
  (memoize (fn [] (osm/create (config/osm-base-url) (config/osm-jobs-bucket)))))

(defn list-jobs
  []
  (map :state
       ((comp :objects cheshire/decode)
        (osm/query (osm-jobs-client) {:state.user (:shortUsername current-user)}))))
