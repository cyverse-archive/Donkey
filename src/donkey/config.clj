(ns donkey.config
  (:use [clojure.string :only (blank? split)])
  (:require [clojure-commons.props :as cc-props]
            [clojure.data.json :as json]
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
   configuration-is-valid (atom true))

(defn- record-missing-prop
  "Records a property that is missing.  Instead of failing on the first
   missing parameter, we log the missing parameter, mark the configuration
   as invalid and keep going so that we can log as many configuration errors
   as possible in one run."
  [prop-name]
  (log/error "required configuration setting" prop-name "is empty or"
             "undefined")
  (reset! configuration-is-valid false))

(defn- record-invalid-prop
  "Records a property that has an invalid value.  Instead of failing on the
   first missing parameter, we log the missing parameter, mark the
   configuration as invalid and keep going so that we can log as many
   configuration errors as possible in one run."
  [prop-name t]
  (log/error "invalid configuration setting for" prop-name ":" t)
  (reset! configuration-is-valid false))

(defn- get-required-prop
  "Gets a required property from the properties that were loaded from
   Zookeeper."
  [prop-name]
  (let [value (get @props prop-name "")]
    (when (blank? value)
      (record-missing-prop prop-name))
    value))

(defn- vector-from-prop
  "Derives a list of values from a single comma-delimited value."
  [value]
  (split value #", *"))

(defn- get-required-vector-prop
  "Gets a required vector property from the properties that were loaded from
   Zookeeper."
  [prop-name]
  (vector-from-prop (get-required-prop prop-name)))

(defn- string-to-int
  "Attempts to convert a String property to an integer property.  Returns nil
   if the property can't be converted."
  [prop-name value]
  (try
    (Integer/parseInt value)
    (catch NumberFormatException e
      (do (record-invalid-prop prop-name e) 0))))

(defn- get-required-integer-prop
  "Gets a required integer property from the properties that wer loaded from
   Zookeeper."
  [prop-name]
  (let [value (get-required-prop prop-name)]
    (if (blank? value)
      0
      (string-to-int prop-name value))))

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

(defmacro INT
  "Defines a required integer property."
  {:private true}
  [fname desc pname]
  `(defn ~fname ~desc [] (get-required-integer-prop ~pname)))

(def ^:dynamic refgens (atom nil))

(defn reference-genomes
  "Pulls in reference_genomes.json from the classpath, parses it as JSON,
   and returns a HashMap (for compatibility with metadactyl)."
  []
  (if (nil? @refgens)
    (reset! refgens 
            (java.util.HashMap. 
              (json/read-json 
                (slurp (cc-props/find-resources-file "reference_genomes.json")) 
                false)))
    @refgens))

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

(STR zoidberg-base-url
  "The base URL to use when connecting to Zoidberg."
  "donkey.zoidberg.base-url")

(INT zoidberg-connection-timeout
  "The maximum number of milliseconds to wait for a connection to Zoidberg."
  "donkey.zoidberg.connection-timeout")

(STR zoidberg-encoding
  "The character encoding to use when communicating with Zoidberg."
  "donkey.zoidberg.encoding")

(STR workspace-root-app-group
  "The name of the root app group in each user's workspace."
  "donkey.workspace.root-app-group")

(STR workspace-default-app-groups
  "The names of the app groups that appear immediately beneath the root app
   group in each user's workspace."
  "donkey.workspace.default-app-groups")

(INT workspace-dev-app-group-index
  "The index of the category within a user's workspace for apps under
   development."
  "donkey.workspace.dev-app-group-index")

(INT workspace-favorites-app-group-index
  "The index of the category within a user's workspace for favorite apps."
  "donkey.workspace.favorites-app-group-index")

(defn configuration-valid
  "Returns the value of the configuration validity flag.  This function should
   only be called after Donkey has been initialized."
  []
  @configuration-is-valid)
