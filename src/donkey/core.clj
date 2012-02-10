(ns donkey.core
  (:gen-class)
  (:use [compojure.core]
        [donkey.beans]
        [donkey.config]
        [donkey.metadactyl]
        [donkey.service]
        [ring.middleware keyword-params nested-params])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.tools.logging :as log]
            [clojure-commons.clavin-client :as cl]
            [ring.adapter.jetty :as jetty]))

(defn- trap
  "Traps any exception thrown by a service and returns an appropriate
   repsonse."
  [f]
  (try
    (f)
    (catch IllegalArgumentException e (failure-response e))
    (catch IllegalStateException e (failure-response e))
    (catch Throwable t (error-response t))))

(defroutes donkey-routes
  (GET "/" []
       "Welcome to Donkey!  I've mastered the stairs!\n")
  (GET "/get-workflow-elements/:element-type" [element-type]
       (trap #(get-workflow-elements element-type)))
  (GET "/get-all-analysis-ids" []
       (trap #(get-all-app-ids)))
  (POST "/delete-categories" [:as {body :body}]
        (trap #(delete-categories body)))
  (GET "/validate-analysis-for-pipelines/:app-id" [app-id]
       (trap #(validate-app-for-pipelines app-id)))
  (GET "/analysis-data-objects/:app-id" [app-id]
       (trap #(get-data-objects-for-app app-id)))
  (POST "/categorize-analyses" [:as {body :body}]
        (trap #(categorize-apps body)))
  (GET "/get-analysis-categories/:category-set" [category-set]
       (trap #(get-app-categories category-set)))
  (POST "/can-export-app" [:as {body :body}]
        (trap #(can-export-app body)))
  (POST "/add-analysis-to-group" [:as {body :body}]
        (trap #(add-app-to-group body)))
  (GET "/get-analysis/:app-id" [app-id]
       (trap #(get-app app-id)))
  (GET "/get-public-analyses" []
       (trap #(get-public-analyses)))
  (GET "/get-only-analysis-groups/:workspace-id" [workspace-id]
       (trap #(get-only-analysis-groups workspace-id)))
  (GET "/export-template/:template-id" [template-id]
       (trap #(export-template template-id)))
  (GET "/export-workflow/:app-id" [app-id]
       (trap #(export-workflow app-id)))
  ;(POST "/permanently-delete-workflow" [:as {body :body}]
  ;      (trap #(permanently-delete-workflow body)))
  (POST "/delete-workflow" [:as {body :body}]
        (trap #(delete-workflow body)))
  (POST "/preview-template" [:as {body :body}]
        (trap #(preview-template body)))
  (POST "/preview-workflow" [:as {body :body}]
        (trap #(preview-workflow body)))
  (POST "/update-template" [:as {body :body}]
        (trap #(update-template body)))
  (POST "/force-update-workflow" [:as {body :body}]
        (trap #(force-update-workflow body)))
  (POST "/update-workflow" [:as {body :body}]
        (trap #(update-workflow body)))
  (POST "/import-template" [:as {body :body}]
        (trap #(import-template body)))
  (POST "/import-workflow" [:as {body :body}]
        (trap #(import-workflow body)))
  
  (route/not-found (unrecognized-path-response)))

(defn site-handler [routes]
  (-> routes
      wrap-keyword-params
      wrap-nested-params))

(defn load-configuration
  "Loads the configuration properties from Zookeeper."
  []
  (cl/with-zk
    zk-url
    (when (not (cl/can-run?))
      (log/warn "THIS APPLICATION CANNOT RUN ON THIS MACHINE. SO SAYETH ZOOKEEPER.")
      (log/warn "THIS APPLICATION WILL NOT EXECUTE CORRECTLY.")
      (System/exit 1))
    (reset! props (cl/properties "donkey")))
  (log/warn @props)
  (init-registered-beans)
  (when (not (configuration-valid))
    (log/warn "THE CONFIGURATION IS INVALID - EXITING NOW")
    (System/exit 1)))

(def app
  (site-handler donkey-routes))

(defn -main
  [& args]
  (load-configuration)
  (log/warn "Listening on" (listen-port))
  (jetty/run-jetty app {:port (listen-port)}))
