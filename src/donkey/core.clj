(ns donkey.core
  (:gen-class)
  (:use [clojure-commons.query-params :only (wrap-query-params)]
        [compojure.core]
        [donkey.beans]
        [donkey.config]
        [donkey.metadactyl]
        [donkey.notifications]
        [donkey.service]
        [donkey.user-sessions]
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

(defroutes secured-routes
  (GET "/bootstrap" []
       (trap #(bootstrap)))

  (POST "/notifications/get-messages" [:as req]
        (trap #(get-messages req)))

  (POST "/notifications/get-unseen-messages" [:as req]
        (trap #(get-unseen-messages req)))

  (POST "/notifications/:params" [:as req]
        (trap #(delete-notifications req)))

  (GET "/template/:app-id" [app-id]
       (trap #(get-app app-id)))

  (PUT "/workspaces/:workspace-id/newexperiment" [workspace-id :as {body :body}]
       (trap #(run-experiment body workspace-id)))

  (GET "/workspaces/:workspace-id/executions/list" [workspace-id]
       (trap #(get-experiments workspace-id)))

  (PUT "/workspaces/:workspace-id/executions/delete" [workspace-id :as {body :body}]
       (trap #(delete-experiments body workspace-id)))

  (POST "/rate-analysis" [:as {body :body}]
        (trap #(rate-app body)))

  (POST "/delete-rating" [:as {body :body}]
        (trap #(delete-rating body)))

  (GET "/search-analyses/:search-term" [search-term]
       (trap #(search-apps search-term)))

  (GET "/get-analyses-in-group/:app-group-id" [app-group-id]
       (trap #(list-apps-in-group app-group-id)))

  (GET "/list-analyses-for-pipeline/:app-group-id" [app-group-id]
       (trap #(list-apps-in-group app-group-id)))

  (POST "/update-favorites" [:as {body :body}]
        (trap #(update-favorites body)))

  (GET "/edit-template/:app-id" [app-id]
       (trap #(edit-app app-id)))

  (GET "/copy-template/:app-id" [app-id]
       (trap #(copy-app app-id)))

  (POST "/make-analysis-public" [:as {body :body}]
        (trap #(make-app-public body)))
  
  (route/not-found (unrecognized-path-response)))

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

  (POST "/can-export-analysis" [:as {body :body}]
        (trap #(can-export-app body)))

  (POST "/add-analysis-to-group" [:as {body :body}]
        (trap #(add-app-to-group body)))

  (GET "/get-analysis/:app-id" [app-id]
       (trap #(get-app app-id)))

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

  (GET "/get-property-values/:job-id" [job-id]
       (trap #(get-property-values job-id)))
  
  (GET "/sessions/:user" [user]
       (trap #(user-session user)))
  
  (POST "/sessions/:user" [user :as {body :body}]
        (trap #(user-session user (slurp body))))

  (context "/secured" []
           (store-current-user secured-routes #(cas-server) #(server-name)))

  (route/not-found (unrecognized-path-response)))

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

(defn site-handler [routes]
  (-> routes
      wrap-keyword-params
      wrap-nested-params
      wrap-query-params))

(def app 
  (site-handler donkey-routes))

(defn -main
  [& args]
  (load-configuration)
  (log/warn "Listening on" (listen-port))
  (jetty/run-jetty app {:port (listen-port)}))
