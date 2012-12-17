(ns donkey.config
  (:use [clojure.string :only (blank? split)])
  (:require [clojure-commons.props :as cc-props]
            [clojure.tools.logging :as log]))

(defn- file-exists-on-classpath
  "Determines if a file exists somewhere on the classpath."
  [filename]
  (not (nil? (cc-props/find-properties-file filename))))

(defn- find-first-existing-file
  "Finds the first file in a list of files that exists on the classpath."
  [files]
  (first (filter file-exists-on-classpath files)))

(defn- prop-files
  "The names of the properties files to look for."
  []
  ["zkhosts.properties" "donkey.properties"])

(defn prop-file
  "The name of the properties file that is being used."
  []
  (find-first-existing-file (prop-files)))

(defn zk-props
  "The properties loaded from the properties file."
  []
  (cc-props/parse-properties (prop-file)))

(defn zk-url
  "The URL used to connect to zookeeper."
  []
  (get (zk-props) "zookeeper"))

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

(INT listen-port
  "The port that donkey listens to."
  "donkey.app.listen-port")

(STR metadactyl-base-url
  "The base URL to use when connecting to secured Metadactyl services."
  "donkey.metadactyl.base-url")

(STR metadactyl-unprotected-base-url
  "The base URL to use when connecting to unsecured Metadactyl services."
  "donkey.metadactyl.unprotected-base-url")

(STR notificationagent-base-url
  "The base URL to use when connecting to the notification agent."
  "donkey.notificationagent.base-url")

(STR cas-server
  "The base URL used to connect to the CAS server."
  "donkey.cas.cas-server")

(STR server-name
  "The name of the local server."
  "donkey.cas.server-name")

(STR uid-domain
  "The domain name to append to the user identifier to get the fully qualified
   user identifier."
  "donkey.uid.domain")

(STR riak-base-url
  "The base URL for the Riak HTTP API. Used for user sessions."
  "donkey.sessions.base-url")

(STR riak-sessions-bucket
  "The bucket in Riak to retrieve user sessions from."
  "donkey.sessions.bucket")

(STR riak-prefs-bucket
   "The bucket in Riak to retrieve user preferences from."
   "donkey.preferences.bucket")

(STR riak-search-hist-bucket
   "The bucket in Riak to use for the storage of user search history."
   "donkey.search-history.bucket")

(STR userinfo-base-url
  "The base URL for the user info API."
  "donkey.userinfo.base-url")

(STR userinfo-key
  "The key to use when authenticating to the user info API."
  "donkey.userinfo.client-key")

(STR userinfo-secret
  "The secret to use when authenticating to the user info API."
  "donkey.userinfo.password")

(STR jex-base-url
  "The base URL for the JEX."
  "donkey.jex.base-url")

(INT default-user-search-result-limit
  "The default limit for the number of results for a user info search.  Note
   this is the maximum number of results returned by trellis for any given
   search.  Our aggregate search may return the limit times the number of
   search types."
  "donkey.userinfo.default-search-limit")

(STR nibblonian-base-url
  "The base URL for the Nibblonian data management services."
  "donkey.nibblonian.base-url")

(STR scruffian-base-url
  "The base URL for the Scruffian file export and import services."
  "donkey.scruffian.base-url")

(STR tree-parser-url
  "The URL for the tree parser service."
  "donkey.tree-viewer.base-url")

(STR buggalo-path
  "The path to the buggalo executable file."
  "donkey.tree-viewer.buggalo-path")

(VEC supported-tree-formats
  "The list of supported tree formats."
  "donkey.tree-viewer.accepted-tree-formats")

(STR tree-url-bucket
  "The bucket in Riak to use for the storage of tree viewer URLs."
  "donkey.tree-viewer.bucket")

(STR es-url
  "The URL for Elastic Search"
  "donkey.infosquito.es-url")

(defn configuration-valid
  "Returns the value of the configuration validity flag.  This function should
   only be called after Donkey has been initialized."
  []
  @configuration-is-valid)
