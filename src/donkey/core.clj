(ns donkey.core
  (:gen-class)
  (:use [clojure-commons.query-params :only (wrap-query-params)]
        [compojure.core]
        [donkey.beans]
        [donkey.config]
        [donkey.metadactyl]
        [donkey.service]
        [donkey.user-attributes]
        [donkey.user-info]
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
  (GET "/bootstrap" [:as req]
       (trap #(bootstrap req)))

  (POST "/notifications/get-messages" [:as req]
        (trap #(get-messages req)))

  (POST "/notifications/get-unseen-messages" [:as req]
        (trap #(get-unseen-messages req)))

  (POST "/notifications/:params" [:as req]
        (trap #(delete-notifications req)))

  (GET "/template/:app-id" [app-id :as req]
       (trap #(get-app-secured req app-id)))

  (PUT "/workspaces/:workspace-id/newexperiment" [workspace-id :as {body :body}]
       (trap #(run-experiment body workspace-id)))

  (GET "/workspaces/:workspace-id/executions/list" [workspace-id :as req]
       (trap #(get-experiments req workspace-id)))

  (PUT "/workspaces/:workspace-id/executions/delete" [workspace-id :as {body :body}]
       (trap #(delete-experiments body workspace-id)))

  (POST "/rate-analysis" [:as {body :body}]
        (trap #(rate-app body)))

  (POST "/delete-rating" [:as {body :body}]
        (trap #(delete-rating body)))

  (GET "/search-analyses/:search-term" [search-term :as req]
       (trap #(search-apps req search-term)))

  (GET "/get-analyses-in-group/:app-group-id" [app-group-id :as req]
       (trap #(list-apps-in-group req app-group-id)))

  (GET "/list-analyses-for-pipeline/:app-group-id" [app-group-id :as req]
       (trap #(list-apps-in-group req app-group-id)))

  (POST "/update-favorites" [:as {body :body}]
        (trap #(update-favorites body)))

  (GET "/edit-template/:app-id" [app-id :as req]
       (trap #(edit-app req app-id)))

  (GET "/copy-template/:app-id" [app-id :as req]
       (trap #(copy-app req app-id)))

  (POST "/make-analysis-public" [:as {body :body}]
        (trap #(make-app-public body)))

  (GET "/sessions" []
       (trap #(user-session)))

  (POST "/sessions" [:as {body :body}]
        (trap #(user-session (slurp body))))

  (GET "/user-search/:search-string" [search-string :as req]
       (trap #(user-search search-string (get-in req [:headers "range"]))))

  (route/not-found (unrecognized-path-response)))

(defroutes donkey-routes
  (GET "/" []
       "Welcome to Donkey!  I've mastered the stairs!\n")

  (GET "/get-workflow-elements/:element-type" [element-type :as req]
       (trap #(get-workflow-elements req element-type)))

  (GET "/search-deployed-components/:search-term" [search-term :as req]
       (trap #(search-deployed-components req search-term)))

  (GET "/get-all-analysis-ids" [:as req]
       (trap #(get-all-app-ids req)))

  (POST "/delete-categories" [:as {body :body}]
        (trap #(delete-categories body)))

  (GET "/validate-analysis-for-pipelines/:app-id" [app-id :as req]
       (trap #(validate-app-for-pipelines req app-id)))

  (GET "/analysis-data-objects/:app-id" [app-id :as req]
       (trap #(get-data-objects-for-app req app-id)))

  (POST "/categorize-analyses" [:as {body :body}]
        (trap #(categorize-apps body)))

  (GET "/get-analysis-categories/:category-set" [category-set :as req]
       (trap #(get-app-categories req category-set)))

  (POST "/can-export-analysis" [:as {body :body}]
        (trap #(can-export-app body)))

  (POST "/add-analysis-to-group" [:as {body :body}]
        (trap #(add-app-to-group body)))

  (GET "/get-analysis/:app-id" [app-id :as req]
       (trap #(get-app req app-id)))

  (GET "/get-only-analysis-groups/:workspace-id" [workspace-id :as req]
       (trap #(get-only-analysis-groups req workspace-id)))

  (GET "/export-template/:template-id" [template-id :as req]
       (trap #(export-template req template-id)))

  (GET "/export-workflow/:app-id" [app-id :as req]
       (trap #(export-workflow req app-id)))

  (POST "/export-deployed-components" [:as {body :body}]
        (trap #(export-deployed-components body)))

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

  (POST "/import-tools" [:as {body :body}]
        (trap #(import-tools body)))

  (GET "/get-property-values/:job-id" [job-id :as req]
       (trap #(get-property-values req job-id)))

  (POST "/send-notification" [:as req]
        (trap #(send-notification req)))

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
