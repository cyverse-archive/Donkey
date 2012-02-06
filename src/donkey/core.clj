(ns donkey.core
  (:gen-class)
  (:use [compojure.core]
        [donkey.config]
        [ring.middleware keyword-params nested-params])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.tools.logging :as log]
            [clojure-commons.props :as cc-props]
            [clojure-commons.clavin-client :as cl]
            [ring.adapter.jetty :as jetty]))

(defroutes donkey-routes
  (GET "/" [] "Welcome to Donkey!  I've mastered the stairs!\n"))

(defn site-handler [routes]
  (-> routes
      wrap-keyword-params
      wrap-nested-params))

(def app
  (site-handler donkey-routes))

(defn -main
  [& args]
  (def zkprops (cc-props/parse-properties prop-file))
  (def zkurl (get zkprops "zookeeper"))

  ;; Load the configuration from zookeeper.
  (cl/with-zk
    zkurl
    (when (not (cl/can-run?))
      (log/warn "THIS APPLICATION CANNOT RUN ON THIS MACHINE. SO SAYETH ZOOKEEPER.")
      (log/warn "THIS APPLICATION WILL NOT EXECUTE CORRECTLY.")
      (System/exit 1))
    (reset! props (cl/properties "donkey")))

  ;; Start the server.
  (log/warn @props)
  (log/warn "Listening on" (listen-port))
  (jetty/run-jetty (site-handler donkey-routes) {:port (listen-port)}))
