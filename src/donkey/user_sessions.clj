(ns donkey.user-sessions
  (:use [donkey.config])
  (:require [clojure-commons.osm :as osm]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(defn user-session?
  "Checks to see if the user's session already exists in the OSM."
  [user]
  (let [osmcl (osm/create osm-base-url osm-session-bucket)
        query {"state.user" user}]
    (> (count (:objects (json/read-json (osm/query osmcl query)))) 0)))

(defn user-session-id
  "Gets the OSM id for a user's session document."
  [user]
  (let [osmcl (osm/create osm-base-url osm-session-bucket)
        query {"state.user" user}]
    (:object_persistence_uuid (first (:objects (json/read-json (osm/query osmcl query)))))))

(defn new-session
  "Creates a new session document for a user in the OSM."
  [user session-obj]
  (let [osmcl (osm/create osm-base-url osm-session-bucket)]
    (osm/save-object osmcl (assoc session-obj :user user))))

(defn set-session
  "Updates an existing session document for a user in the OSM."
  [user session-obj]
  (let [osmcl (osm/create osm-base-url osm-session-bucket)
        sid   (user-session-id user)]
    (osm/update-object osmcl sid (assoc session-obj :user user))))

(defn user-session
  "Sets and gets the user session from the OSM."
  ([user]
    (json/json-str 
      (if (user-session? user) 
        (let [osmcl (osm/create osm-base-url osm-session-bucket)
              query {"state.user" user}]
          (:state (first (:objects (json/read-json (osm/query osmcl query))))))
        {})))
  ([user session-str]
    (if (not (user-session? user))
      (new-session user (json/read-json session-str))
      (set-session user (json/read-json session-str)))))

