(ns donkey.util.config
  (:use [slingshot.slingshot :only [throw+]])
  (:require [clojure-commons.config :as cc]
            [clojure-commons.error-codes :as ce]
            [clojure.core.memoize :as memo]
            [clj-jargon.jargon :as jg]))

(def ^:private props
  "A ref for storing the configuration properties."
  (ref nil))

(def ^:private config-valid
  "A ref for storing a configuration validity flag."
  (ref true))

(def ^:private configs
  "A ref for storing the symbols used to get configuration settings."
  (ref []))

(cc/defprop-int listen-port
  "The port that donkey listens to."
  [props config-valid configs]
  "donkey.app.listen-port")

(cc/defprop-str environment-name
  "The name of the environment that this instance of Donkey belongs to."
  [props config-valid configs]
  "donkey.app.environment-name")

(cc/defprop-str cas-server
  "The base URL used to connect to the CAS server."
  [props config-valid configs]
  "donkey.cas.cas-server")

(cc/defprop-str server-name
  "The name of the local server."
  [props config-valid configs]
  "donkey.cas.server-name")

(cc/defprop-str uid-domain
  "The domain name to append to the user identifier to get the fully qualified
   user identifier."
  [props config-valid configs]
  "donkey.uid.domain")

(cc/defprop-optboolean notification-routes-enabled
  "Enables or disables notification endpoints."
  [props config-valid configs]
  "donkey.routes.notifications" true)

(cc/defprop-optboolean metadata-routes-enabled
  "Enables or disables metadata endpoints."
  [props config-valid configs]
  "donkey.routes.metadata" true)

(cc/defprop-optboolean pref-routes-enabled
  "Enables or disables preferences endpoints."
  [props config-valid configs]
  "donkey.routes.prefs" true)

(cc/defprop-optboolean user-info-routes-enabled
  "Enables or disables user-info endpoints."
  [props config-valid configs]
  "donkey.routes.user-info" true)

(cc/defprop-optboolean data-routes-enabled
  "Enables or disables data endpoints."
  [props config-valid configs]
  "donkey.routes.data" true)

(cc/defprop-optboolean tree-viewer-routes-enabled
  "Enables or disables tree-viewer endpoints."
  [props config-valid configs]
  "donkey.routes.tree-viewer" true)

(cc/defprop-optboolean session-routes-enabled
  "Enables or disables user session endpoints."
  [props config-valid configs]
  "donkey.routes.session" true)

(cc/defprop-optboolean collaborator-routes-enabled
  "Enables or disables collaborator routes."
  [props config-valid configs]
  "donkey.routes.collaborator" true)

(cc/defprop-optboolean fileio-routes-enabled
  "Enables or disables the fileio routes."
  [props config-valid configs]
  "donkey.routes.fileio" true)

(cc/defprop-str iplant-email-base-url
  "The base URL to use when connnecting to the iPlant email service."
  [props config-valid configs metadata-routes-enabled]
  "donkey.email.base-url")

(cc/defprop-str tool-request-dest-addr
  "The destination email address for tool request messages."
  [props config-valid configs metadata-routes-enabled]
  "donkey.email.tool-request-dest")

(cc/defprop-str tool-request-src-addr
  "The source email address for tool request messages."
  [props config-valid configs metadata-routes-enabled]
  "donkey.email.tool-request-src")

(cc/defprop-str feedback-dest-addr
  "The destination email address for DE feedback messages."
  [props config-valid configs metadata-routes-enabled]
  "donkey.email.feedback-dest")

(cc/defprop-str metadactyl-base-url
  "The base URL to use when connecting to secured Metadactyl services."
  [props config-valid configs metadata-routes-enabled]
  "donkey.metadactyl.base-url")

(cc/defprop-str metadactyl-unprotected-base-url
  "The base URL to use when connecting to unsecured Metadactyl services."
  [props config-valid configs metadata-routes-enabled]
  "donkey.metadactyl.unprotected-base-url")

(cc/defprop-str notificationagent-base-url
  "The base URL to use when connecting to the notification agent."
  [props config-valid configs notification-routes-enabled]
  "donkey.notificationagent.base-url")

(cc/defprop-str riak-base-url
  "The base URL for the Riak HTTP API. Used for user sessions."
  [props config-valid configs pref-routes-enabled session-routes-enabled
   tree-viewer-routes-enabled]
  "donkey.sessions.base-url")

(cc/defprop-str riak-sessions-bucket
  "The bucket in Riak to retrieve user sessions from."
  [props config-valid configs session-routes-enabled]
  "donkey.sessions.bucket")

(cc/defprop-str riak-prefs-bucket
  "The bucket in Riak to retrieve user preferences from."
  [props config-valid configs pref-routes-enabled]
  "donkey.preferences.bucket")

(cc/defprop-str riak-search-hist-bucket
  "The bucket in Riak to use for the storage of user search history."
  [props config-valid configs pref-routes-enabled]
  "donkey.search-history.bucket")

(cc/defprop-str userinfo-base-url
  "The base URL for the user info API."
  [props config-valid configs user-info-routes-enabled]
  "donkey.userinfo.base-url")

(cc/defprop-str userinfo-key
  "The key to use when authenticating to the user info API."
  [props config-valid configs user-info-routes-enabled]
  "donkey.userinfo.client-key")

(cc/defprop-str userinfo-secret
  "The secret to use when authenticating to the user info API."
  [props config-valid configs user-info-routes-enabled]
  "donkey.userinfo.password")

(cc/defprop-str jex-base-url
  "The base URL for the JEX."
  [props config-valid configs metadata-routes-enabled]
  "donkey.jex.base-url")

;;;iRODS connection information
(cc/defprop-str irods-home
  "Returns the path to the home directory in iRODS. Usually /iplant/home"
  [props config-valid configs data-routes-enabled]
  "donkey.irods.home")

(cc/defprop-str irods-user
  "Returns the user that porklock should connect as."
  [props config-valid configs data-routes-enabled]
  "donkey.irods.user")

(cc/defprop-str irods-pass
  "Returns the iRODS user's password."
  [props config-valid configs data-routes-enabled]
  "donkey.irods.pass")

(cc/defprop-str irods-host
  "Returns the iRODS hostname/IP address."
  [props config-valid configs data-routes-enabled]
  "donkey.irods.host")

(cc/defprop-str irods-port
  "Returns the iRODS port."
  [props config-valid configs data-routes-enabled]
  "donkey.irods.port")

(cc/defprop-str irods-zone
  "Returns the iRODS zone."
  [props config-valid configs data-routes-enabled]
  "donkey.irods.zone")

(cc/defprop-optstr irods-resc
  "Returns the iRODS resource."
  [props config-valid configs data-routes-enabled]
  "donkey.irods.resc")

(def jargon-cfg
  (memo/memo 
    #(jg/init 
       (irods-host)
       (irods-port)
       (irods-user)
       (irods-pass)
       (irods-home)
       (irods-zone)
       (irods-resc))))

;;; Garnish configuration
(cc/defprop-str garnish-type-attribute
  "The value that goes in the attribute column for AVUs that define a file type."
  [props config-valid configs data-routes-enabled]
  "donkey.garnish.type-attribute")

(cc/defprop-str filetype-script
  "The path to a perl script that detects filetypes."
  [props config-valid configs data-routes-enabled]
  "donkey.garnish.filetype-script")

(cc/defprop-long filetype-read-amount
  "The size, in bytes as a long, of the sample read from iRODS"
  [props config-valid configs data-routes-enabled]
  "donkey.garnish.filetype-read-amount")
;;; End of Garnish configuration

;;; File IO configuration
(cc/defprop-str fileio-temp-dir
  "The directory, in iRODS, to use as temp storage for uploads."
  [props config-valid configs fileio-routes-enabled]
  "donkey.fileio.temp-dir")

(cc/defprop-str fileio-curl-path
  "The path on the cluster to the curl tool."
  [props config-valid configs fileio-routes-enabled]
  "donkey.fileio.curl-path")

(cc/defprop-vec fileio-admin-users
  "The admin users in iRODS."
  [props config-valid configs fileio-routes-enabled]
  "donkey.fileio.admin-users")

(cc/defprop-str fileio-service-name
  "The old service name for fileio"
  [props config-valid configs fileio-routes-enabled]
  "donkey.fileio.service-name")
;;; End File IO configuration

(cc/defprop-int default-user-search-result-limit
  "The default limit for the number of results for a user info search.  Note
   this is the maximum number of results returned by trellis for any given
   search.  Our aggregate search may return the limit times the number of
   search types."
  [props config-valid configs user-info-routes-enabled]
  "donkey.userinfo.default-search-limit")

(cc/defprop-str nibblonian-base-url
  "The base URL for the Nibblonian data management services."
  [props config-valid configs metadata-routes-enabled data-routes-enabled
   tree-viewer-routes-enabled]
  "donkey.nibblonian.base-url")

(cc/defprop-str scruffian-base-url
  "The base URL for the Scruffian file export and import services."
  [props config-valid configs tree-viewer-routes-enabled]
  "donkey.scruffian.base-url")

(cc/defprop-str tree-parser-url
  "The URL for the tree parser service."
  [props config-valid configs tree-viewer-routes-enabled]
  "donkey.tree-viewer.base-url")

(cc/defprop-str tree-url-bucket
  "The bucket in Riak to use for the storage of tree viewer URLs."
  [props config-valid configs tree-viewer-routes-enabled]
  "donkey.tree-viewer.bucket")

(cc/defprop-str es-url
  "The URL for Elastic Search"
  [props config-valid configs data-routes-enabled]
  "donkey.infosquito.es-url")

(defn- validate-config
  "Validates the configuration settings after they've been loaded."
  []
  (when-not (cc/validate-config configs config-valid)
    (throw+ {:error_code ce/ERR_CONFIG_INVALID})))

(defn load-config-from-file
  "Loads the configuration settings from a file."
  []
  (cc/load-config-from-file (System/getenv "IPLANT_CONF_DIR") "donkey.properties" props)
  (cc/log-config props)
  (validate-config))

(defn load-config-from-zookeeper
  "Loads the configuration settings from Zookeeper."
  []
  (cc/load-config-from-zookeeper props "donkey")
  (cc/log-config props)
  (validate-config))
