(ns donkey.util.db
  (:use [clj-time.core :only [default-time-zone]]
        [clj-time.format :only [formatter parse]]
        [donkey.util.config]
        [korma.db]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [clojure.string :as string]
            [clojure-commons.error-codes :as ce])
  (:import [java.sql Timestamp]))

(defn- create-db-spec
  "Creates the database connection spec to use when accessing the database
   using Korma."
  []
  {:classname   (db-driver-class)
   :subprotocol (db-subprotocol)
   :subname     (str "//" (db-host) ":" (db-port) "/" (db-name))
   :user        (db-user)
   :password    (db-password)})

(defn define-database
  "Defines the database connection to use from within Clojure."
  []
  (let [spec (create-db-spec)]
    (defonce de (create-db spec))
    (default-connection de)))

(defn timestamp-from-millis
  "Converts the number of milliseconds since the epoch to a timestamp."
  [millis]
  (when-not (nil? millis)
    (Timestamp. millis)))

(def ^:private millis-str-regex #"^\d+$")

(def ^:private timestamp-parser
  (formatter (default-time-zone)
             "EEE MMM dd YYYY HH:mm:ss 'GMT'Z"
             "YYYY MMM dd HH:mm:ss"
             "YYYY-MM-dd-HH-mm-ss.SSS"))

(defn- strip-time-zone
  "Removes the time zone abbreviation from a date timestamp."
  [s]
  (string/replace s #"\s*\(\w+\)\s*$" ""))

(defn- parse-timestamp
  "Parses a timestamp in one of the accepted formats."
  [s]
  (Timestamp. (.getMillis (parse timestamp-parser (strip-time-zone s)))))

(defn timestamp-from-str
  "Parses a string representation of a timestamp."
  [s]
  (assert (or (nil? s) (string? s)))
  (cond (or (string/blank? s) (= "0" s)) nil
        (re-matches #"\d+" s)            (Timestamp. (Long/parseLong s))
        :else                            (parse-timestamp s)))

(defn millis-from-timestamp
  "Converts a timestamp to the number of milliseconds since the epoch."
  [timestamp]
  (when-not (nil? timestamp)
    (.getTime timestamp)))
