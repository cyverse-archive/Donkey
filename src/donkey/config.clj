(ns donkey.config
  (:use [clojure.string :only (blank? split)])
  (:require [clojure-commons.props :as cc-props]
            [clojure.tools.logging :as log]))

(def
  ^{:doc "The name of the properties file."}
  prop-file "donkey.properties")

(def
  ^{:doc "The properties loaded from the properties file."}
   zk-props (cc-props/parse-properties prop-file))

(def
  ^{:doc "The URL used to connect to zookeeper."}
   zk-url (get zk-props "zookeeper"))

(def
  ^{:doc "The properites that have been loaded from Zookeeper."}
   props (atom nil))

(def
  ^{:doc "True if the configuration is valid."}
   configuration-valid (atom true))

(defn- record-missing-prop
  "Records a property that is missing.  Instead of failing on the first
   missing parameter, we log the missing parameter, mark the configuration
   as invalid and keep going so that we can log as many configuration errors
   as possible in one run."
  [prop-name]
  (log/error "required configuration setting," prop-name ", is empty or"
             "undefined")
  (reset! configuration-valid false))

(defn- get-required-prop
  "Gets a required property from the properties that were loaded from
   Zookeeper."
  [prop-name]
  (let [value (get @props prop-name)]
    (when (blank? value)
      (record-missing-prop prop-name))))

(defn- vector-from-prop
  "Derives a list of values from a single comma-delimited value."
  [value]
  (split value #", *"))

(defn- get-required-vector-prop
  "Gets a required vector property the properties that were loaded from
   Zookeeper."
  [prop-name]
  (vector-from-prop (get-required-prop prop-name)))

(defmacro STR
  "defines a required string property."
  {:private true}
  [fname desc pname]
  `(defn ~fname ~desc [] (get-required-prop ~pname)))

(defmacro VEC
  "Defines a required vector property."
  {:private true}
  [fname desc pname]
  `(defn ~fname ~desc [] (get-required-vector-prop ~pname)))

(STR listen-port 
  "The port that donkey listens to."
  "donkey.app.listen-port")

(STR db-driver-class
  "The name of the JDBC driver to use."
  "donkey.db.driver" )

(STR db-subprotocol
  "The subprotocol to use when connecting to the database (e.g.
   postgresql)."
  "donkey.db.subprotocol")

(STR db-host
  "The host name or IP address to use when
   connecting to the database."
  "donkey.db.host")

(STR db-port
  "The port number to use when connecting to the database."
  "donkey.db.port")

(STR db-name
  "The name of the database to connect to."
  "donkey.db.name")

(STR db-user
  "The username to use when authenticating to the database."
  "donkey.db.user")

(STR db-password
  "The password to use when authenticating to the database."
  "donkey.db.password")

(VEC hibernate-resources
  "The names of the hibernate resource files to include in the Hibernate
   session factory configuration."
  "donkey.hibernate.resources")

(VEC hibernate-packages
  "The names of Java packages that Hibernate needs to scan for JPA
   annotations."
  "donkey.hibernate.packages")

(STR hibernate-dialect
  "The dialect that Hibernate should use when generating SQL."
  "donkey.hibernate.dialect")
