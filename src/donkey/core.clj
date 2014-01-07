(ns donkey.core
  (:gen-class)
  (:use [clojure.java.io :only [file]]
        [clojure-commons.lcase-params :only [wrap-lcase-params]]
        [clojure-commons.query-params :only [wrap-query-params]]
        [clj-jargon.init :only [with-jargon]]
        [compojure.core]
        [donkey.routes.admin]
        [donkey.routes.callbacks]
        [donkey.routes.data]
        [donkey.routes.fileio]
        [donkey.routes.metadata]
        [donkey.routes.misc]
        [donkey.routes.notification]
        [donkey.routes.pref]
        [donkey.routes.session]
        [donkey.routes.tree-viewer]
        [donkey.routes.user-info]
        [donkey.routes.collaborator]
        [donkey.routes.filesystem]
        [donkey.routes.search]
        [donkey.routes.coge]
        [donkey.auth.user-attributes]
        [donkey.util]
        [donkey.util.service]
        [ring.middleware keyword-params multipart-params]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [compojure.route :as route]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [donkey.services.metadata.apps :as apps]
            [donkey.util.config :as config]
            [donkey.util.db :as db]
            [donkey.services.fileio.controllers :as fileio]
            [donkey.util.messaging :as messages]
            [donkey.util.icat :as icat]
            [clojure.tools.nrepl.server :as nrepl]))

(defn delayed-handler
  [routes-fn]
  (fn [req]
    (let [handler ((memoize routes-fn))]
      (handler req))))

(defn secured-routes
  []
  (flagged-routes
   (secured-notification-routes)
   (secured-metadata-routes)
   (secured-pref-routes)
   (secured-collaborator-routes)
   (secured-user-info-routes)
   (secured-tree-viewer-routes)
   (secured-data-routes)
   (secured-session-routes)
   (secured-fileio-routes)
   (secured-filesystem-routes)
   (secured-coge-routes)
   (secured-admin-routes)
   (secured-search-routes)
   (route/not-found (unrecognized-path-response))))

(defn cas-store-user
  [routes]
  (let [f (if (System/getenv "IPLANT_CAS_FAKE") fake-store-current-user store-current-user)]
    (f routes
       config/cas-server
       config/server-name
       config/pgt-callback-base
       config/pgt-callback-path)))

(def secured-handler
  (-> (delayed-handler secured-routes)
      (cas-store-user)))

(defn donkey-routes
  []
  (flagged-routes
   (unsecured-misc-routes)
   (unsecured-notification-routes)
   (unsecured-metadata-routes)
   (unsecured-tree-viewer-routes)
   (unsecured-fileio-routes)
   (unsecured-callback-routes)

   (context "/secured" [] secured-handler)

   (route/not-found (unrecognized-path-response))))

(defn start-nrepl
  []
  (nrepl/start-server :port 7888))

(defn load-configuration-from-file
  "Loads the configuration properties from a file."
  []
  (config/load-config-from-file)
  (db/define-database))

(defn lein-ring-init
  []
  (load-configuration-from-file)
  (messages/messaging-initialization)
  (icat/configure-icat)
  (start-nrepl)
  (future (apps/sync-job-statuses)))

(defn repl-init
  []
  (load-configuration-from-file)
  (icat/configure-icat))

(defn load-configuration-from-zookeeper
  "Loads the configuration properties from Zookeeper."
  []
  (config/load-config-from-zookeeper)
  (db/define-database))

(defn site-handler
  [routes-fn]
  (-> (delayed-handler routes-fn)
    (wrap-multipart-params {:store fileio/store-irods})
    trap-handler
    req-logger
    wrap-keyword-params
    wrap-lcase-params
    wrap-query-params))

(def app
  (site-handler donkey-routes))

(defn -main
  [& _]
  (if (config/load-config-from-file?)
    (load-configuration-from-file)
    (load-configuration-from-zookeeper))
  (log/warn "Listening on" (config/listen-port))
  (start-nrepl)
  (messages/messaging-initialization)
  (icat/configure-icat)
  (future (apps/sync-job-statuses))
  (jetty/run-jetty app {:port (config/listen-port)}))
