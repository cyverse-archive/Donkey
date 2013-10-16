(ns donkey.util.db
  (:use [donkey.util.config]
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

(defn timestamp-from-millis-str
  "Converts a string representing the number of milliseconds since the epoch to a timestamp."
  [millis-str]
  (when-not (or (string/blank? millis-str) (= "0" millis-str))
    (try+
     (Timestamp. (Long/parseLong millis-str))
     (catch NumberFormatException _
       (throw+ {:error_code ce/ERR_ILLEGAL_ARGUMENT
                :details    :INVALID_TIMESTAMP
                :timestamp  millis-str})))))
