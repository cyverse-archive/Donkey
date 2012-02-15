(ns donkey.core
  (:gen-class)
  (:use [clojure-commons.query-params :only (wrap-query-params)]
        [compojure.core]
        [donkey.beans]
        [donkey.config]
        [donkey.filters]
        [donkey.metadactyl]
        [donkey.notifications]
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

(defn get-route-definitions
  "Builds the code required to build the route definitions."
   []
  '(defroutes donkey-routes
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

     (POST "/permanently-delete-workflow" [:as {body :body}]
           (trap #(permanently-delete-workflow body)))

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

     (FILTERED-GET
       "/bootstrap" []
       [store-current-user (cas-server) (server-name)]
       (trap #(bootstrap)))

;     (FILTERED-POST
;       "/notifications/get-messages" [:as {body :body}]
;       [store-current-user (cas-server) (server-name)]
;       (trap #(get-messages body)))
;
;     (FILTERED-POST 
;       "/notifications/get-unseen-messages" [:as {body :body}] ;getUnseenNotifications
;       [store-current-user (cas-server) (server-name)]
;       (trap #(get-unseen-messages body)))
;     
;     (FILTERED-POST 
;       "/notifications/:params" [params :as {body :body}] ;deleteNotifications
;       [store-current-user (cas-server) (server-name)]
;       (trap #(delete-notifications params body)))
;     
;     (FILTERED-GET 
;       "/template/:analysis-id" [analysis-id] ;fetchTemplateAndNotification
;       [store-current-user (cas-server) (server-name)]
;       (trap #(get-template analysis-id)))
;     
;     (FILTERED-PUT 
;       "/workspaces/:workspace-id/newexperiment" [workspace-id :as {body :body}] ;runExperiment
;       [store-current-user (cas-server) (server-name)]
;       (trap #(run-experiment workspace-id body)))
;      
;     (FILTERED-GET 
;       "/workspaces/:workspace-id/executions/list" [workspace-id :as {body :body}] ;retrieveExperiments
;       [store-current-user (cas-server) (server-name)]
;       (trap #(get-experiments workspace-id body)))
;     
;     (FILTERED-PUT 
;       "/workspaces/:workspace-id/executions/delete" [workspace-id :as {body :body}] ;deleteExecutions
;       [store-current-user (cas-server) (server-name)]
;       (trap #(delete-experiments workspace-id body)))
;     
;     (FILTERED-POST 
;       "/rate-analysis" [:as {body :body}] ;rateAnalysis
;       [store-current-user (cas-server) (server-name)]
;       (trap #(rate-analysis body)))
;     
;     (FILTERED-POST 
;       "/delete-rating" [:as {body :body}] ;deleteRating
;       [store-current-user (cas-server) (server-name)]
;       (trap #(delete-rating body)))
;     
;     (FILTERED-GET 
;       "/get-analyses-in-group/:template-group-id" [template-group-id] ;listAnalysesInGroup
;       [store-current-user (cas-server) (server-name)]
;       (trap #(get-analyses-in-group template-group-id)))
;     
;     (FILTERED-GET 
;       "/list-analyses-for-pipeline/:analysis-id" [analysis-id] ;listAnalysesForPipeline
;       [store-current-user (cas-server) (server-name)]
;       (trap #(list-analyses-for-pipeline analysis-id)))
;     
;     (FILTERED-POST 
;       "/update-favorites" [:as {body :body}] ;updateFavorites
;       [store-current-user (cas-server) (server-name)]
;       (trap #(update-favorites body)))
;     
;     (FILTERED-GET 
;       "/edit-template/:analysis-id" [analysis-id] ;editTemplate
;       [store-current-user (cas-server) (server-name)]
;       (trap #(edit-template analysis-id)))
;     
;     (FILTERED-GET 
;       "/copy-template/:analysis-id" [analysis-id] ;copyTemplate
;       [store-current-user (cas-server) (server-name)]
;       (trap #(copy-template analysis-id)))
;     
;     (FILTERED-POST 
;       "/make-analysis-public" [:as {body :body}] ;makeAnalysisPublic
;       [store-current-user (cas-server) (server-name)]
;       (trap #(make-analysis-public body)))
     
     (route/not-found (unrecognized-path-response))))

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
    (System/exit 1))
  (eval (get-route-definitions)))

(defn site-handler [routes]
  (-> routes
      wrap-keyword-params
      wrap-nested-params
      wrap-query-params))

(def app 
  (site-handler (load-configuration)))

(defn -main
  [& args]
  (log/warn "Listening on" (listen-port))
  (jetty/run-jetty (load-configuration) {:port (listen-port)}))
