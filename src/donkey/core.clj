(ns donkey.core
  (:gen-class)
  (:use [clojure.java.io :only [file]]
        [clojure-commons.lcase-params :only [wrap-lcase-params]]
        [clojure-commons.query-params :only [wrap-query-params]]
        [compojure.core]
        [donkey.data-routes]
        [donkey.metadata-routes]
        [donkey.misc-routes]
        [donkey.notification-routes]
        [donkey.pref-routes]
        [donkey.util.service]
        [donkey.session-routes]
        [donkey.tree-viewer-routes]
        [donkey.user-attributes]
        [donkey.user-info-routes]
        [donkey.collaborator-routes]
        [ring.middleware keyword-params])
  (:require [compojure.route :as route]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [donkey.config :as config]))

(defn- flagged-routes
  [& handlers]
  (apply routes (remove nil? handlers)))

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
   (route/not-found (unrecognized-path-response))))

(defn donkey-routes
  []
  (flagged-routes
   (unsecured-misc-routes)
   (unsecured-notification-routes)
   (unsecured-metadata-routes)
   (unsecured-tree-viewer-routes)

   (context "/secured" []
            (store-current-user (secured-routes) config/cas-server config/server-name))

   (route/not-found (unrecognized-path-response))))

(defn load-configuration-from-file
  "Loads the configuration properties from a file."
  []
  (config/load-config-from-file))

(defn load-configuration-from-zookeeper
  "Loads the configuration properties from Zookeeper."
  []
  (config/load-config-from-zookeeper))

(defn delayed-handler
  [routes-fn]
  (fn [req]
    (let [handler ((memoize routes-fn))]
      (handler req))))

(defn site-handler
  [routes-fn]
  (-> (delayed-handler donkey-routes)
      wrap-keyword-params
      wrap-lcase-params
      wrap-query-params))

(def app
  (site-handler donkey-routes))

(defn -main
  [& _]
  (load-configuration-from-zookeeper)
  (log/warn "Listening on" (config/listen-port))
  (jetty/run-jetty app {:port (config/listen-port)}))
