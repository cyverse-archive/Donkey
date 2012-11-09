(ns donkey.core
  (:gen-class)
  (:use [clojure.java.io :only [file]]
        [clojure-commons.query-params :only [wrap-query-params]]
        [compojure.core]
        [donkey.buggalo]
        [donkey.config]
        [donkey.file-listing]
        [donkey.metadactyl]
        [donkey.middleware :only [wrap-lcase-params]]
        [donkey.service]
        [donkey.sharing]
        [donkey.user-attributes]
        [donkey.user-info]
        [donkey.user-sessions]
        [donkey.user-prefs]
        [ring.middleware keyword-params nested-params]
        [slingshot.slingshot :only [try+]])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.clavin-client :as cl]
            [clojure-commons.props :as cp]
            [clojure-commons.error-codes :as ce]
            [ring.adapter.jetty :as jetty]
            [donkey.jex :as jex]
            [donkey.infosquito :as search])
  (:import [java.util UUID]))

(defn- trap
  "Traps any exception thrown by a service and returns an appropriate
   repsonse."
  [f]
  (try+
   (f)
   (catch [:type :error-status] {:keys [res]} res)
   (catch [:type :missing-argument] {:keys [arg]} (missing-arg-response arg))
   (catch [:type :temp-dir-failure] err (temp-dir-failure-response err))
   (catch [:type :tree-file-parse-err] err (tree-file-parse-err-response err))
   (catch ce/error? err (common-error-code &throw-context))
   (catch IllegalArgumentException e (failure-response e))
   (catch IllegalStateException e (failure-response e))
   (catch Throwable t (error-response t))))

(defroutes secured-routes
  (GET "/bootstrap" [:as req]
       (trap #(bootstrap req)))

  (GET "/notifications/messages" [:as req]
       (trap #(get-messages req)))

  (GET "/notifications/unseen-messages" [:as req]
       (trap #(get-unseen-messages req)))

  (GET "/notifications/last-ten-messages" [:as req]
       (trap #(last-ten-messages req)))

  (GET "/notifications/count-messages" [:as req]
       (trap #(count-messages req)))

  (POST "/notifications/delete" [:as req]
        (trap #(delete-notifications req)))

  (POST "/notifications/seen" [:as req]
        (trap #(mark-notifications-as-seen req)))

  (GET "/template/:app-id" [app-id :as req]
       (trap #(get-app-secured req app-id)))

  (PUT "/workspaces/:workspace-id/newexperiment" [workspace-id :as req]
       (trap #(run-experiment req workspace-id)))

  (GET "/workspaces/:workspace-id/executions/list" [workspace-id :as req]
       (trap #(get-experiments req workspace-id)))

  (POST "/workspaces/:workspace-id/executions/list" [workspace-id :as req]
        (trap #(get-selected-experiments req workspace-id)))

  (PUT "/workspaces/:workspace-id/executions/delete" [workspace-id :as req]
       (trap #(delete-experiments req workspace-id)))

  (DELETE "/stop-analysis/:uuid" [uuid :as req]
          (trap #(jex/stop-analysis req uuid)))

  (POST "/rate-analysis" [:as req]
        (trap #(rate-app req)))

  (POST "/delete-rating" [:as req]
        (trap #(delete-rating req)))

  (GET "/search-analyses" [:as req]
       (trap #(search-apps req)))

  (GET "/get-analyses-in-group/:app-group-id" [app-group-id :as req]
       (trap #(list-apps-in-group req app-group-id)))

  (GET "/list-analyses-for-pipeline/:app-group-id" [app-group-id :as req]
       (trap #(list-apps-in-group req app-group-id)))

  (GET "/get-components-in-analysis/:app-id" [app-id :as req]
       (trap #(list-deployed-components-in-app req app-id)))

  (POST "/update-favorites" [:as req]
        (trap #(update-favorites req)))

  (GET "/edit-template/:app-id" [app-id :as req]
       (trap #(edit-app req app-id)))

  (GET "/copy-template/:app-id" [app-id :as req]
       (trap #(copy-app req app-id)))

  (PUT "/import-template" [:as req]
       (trap #(import-template-secured req)))

  (POST "/make-analysis-public" [:as req]
        (trap #(make-app-public req)))

  (GET "/sessions" []
       (trap #(user-session)))

  (POST "/sessions" [:as {body :body}]
        (trap #(user-session (slurp body))))

  (DELETE "/sessions" []
          (trap #(remove-session)))

  (GET "/preferences" []
       (trap #(user-prefs)))

  (POST "/preferences" [:as {body :body}]
        (trap #(user-prefs (slurp body))))

  (DELETE "/preferences" []
          (trap #(remove-prefs)))

  (GET "/search-history" []
       (trap #(search-history)))

  (POST "/search-history" [:as {body :body}]
        (trap #(search-history (slurp body))))

  (DELETE "/search-history" []
          (trap #(clear-search-history)))

  (GET "/user-search/:search-string" [search-string :as req]
       (trap #(user-search search-string (get-in req [:headers "range"]))))

  (GET "/collaborators" [:as req]
       (trap #(get-collaborators req)))

  (POST "/collaborators" [:as req]
        (trap #(add-collaborators req)))

  (POST "/remove-collaborators" [:as req]
        (trap #(remove-collaborators req)))

  (POST "/share" [:as req]
        (trap #(share req)))

  (POST "/unshare" [:as req]
        (trap #(unshare req)))

  (GET "/default-output-dir" [:as {params :params}]
       (trap #(get-default-output-dir (required-param params :name))))

  (POST "/default-output-dir" [:as {body :body}]
        (trap #(reset-default-output-dir body)))

  (GET "/reference-genomes" [:as req]
       (trap #(list-reference-genomes req)))

  (PUT "/reference-genomes" [:as req]
       (trap #(replace-reference-genomes req)))

  (GET "/tree-viewer-urls" [:as {params :params}]
       (trap #(tree-viewer-urls (required-param params :path) (:shortUsername current-user)
                                params)))

  (GET "/search" [:as {params :params}]
       (trap #(search/search params current-user)))

  (GET "/simple-search/iplant" [:as {params :params}]
       (trap #(search/search params current-user)))

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

  (POST "/delete-categories" [:as req]
        (trap #(delete-categories req)))

  (GET "/validate-analysis-for-pipelines/:app-id" [app-id :as req]
       (trap #(validate-app-for-pipelines req app-id)))

  (GET "/analysis-data-objects/:app-id" [app-id :as req]
       (trap #(get-data-objects-for-app req app-id)))

  (POST "/categorize-analyses" [:as req]
        (trap #(categorize-apps req)))

  (GET "/get-analysis-categories/:category-set" [category-set :as req]
       (trap #(get-app-categories req category-set)))

  (POST "/can-export-analysis" [:as req]
        (trap #(can-export-app req)))

  (POST "/add-analysis-to-group" [:as req]
        (trap #(add-app-to-group req)))

  (GET "/get-analysis/:app-id" [app-id :as req]
       (trap #(get-app req app-id)))

  (GET "/get-only-analysis-groups/:workspace-id" [workspace-id :as req]
       (trap #(get-only-analysis-groups req workspace-id)))

  (GET "/list-analysis/:app-id" [app-id :as req]
       (trap #(list-app req app-id)))

  (GET "/export-template/:template-id" [template-id :as req]
       (trap #(export-template req template-id)))

  (GET "/export-workflow/:app-id" [app-id :as req]
       (trap #(export-workflow req app-id)))

  (POST "/export-deployed-components" [:as req]
        (trap #(export-deployed-components req)))

  (POST "/permanently-delete-workflow" [:as req]
        (trap #(permanently-delete-workflow req)))

  (POST "/delete-workflow" [:as req]
        (trap #(delete-workflow req)))

  (POST "/preview-template" [:as req]
        (trap #(preview-template req)))

  (POST "/preview-workflow" [:as req]
        (trap #(preview-workflow req)))

  (POST "/update-template" [:as req]
        (trap #(update-template req)))

  (POST "/force-update-workflow" [:as req]
        (trap #(force-update-workflow req)))

  (POST "/update-workflow" [:as req]
        (trap #(update-workflow req)))

  (POST "/import-template" [:as req]
        (trap #(import-template req)))

  (POST "/import-workflow" [:as req]
        (trap #(import-workflow req)))

  (POST "/import-tools" [:as req]
        (trap #(import-tools req)))

  (POST "/update-analysis" [:as req]
        (trap #(update-app req)))

  (GET "/get-property-values/:job-id" [job-id :as req]
       (trap #(get-property-values req job-id)))

  (POST "/send-notification" [:as req]
        (trap #(send-notification req)))

  (POST "/tree-viewer-urls" [:as {body :body}]
        (trap #(tree-viewer-urls-for body)))

  (GET "/uuid" []
       (string/upper-case (str (UUID/randomUUID))))

  (context "/secured" []
           (store-current-user secured-routes #(cas-server) #(server-name)))

  (route/not-found (unrecognized-path-response)))

(defn init-service
  "Initializes the service after the configuration settings have been loaded."
  []
  (dorun (map (fn [[k v]] (log/warn "CONFIG:" k "=" v)) (sort-by key @props)))
  (when-not (configuration-valid)
    (log/warn "THE CONFIGURATION IS INVALID - EXITING NOW")
    (System/exit 1)))

(defn load-configuration-from-file
  "Loads the configuration properties from a file."
  []
  (let [filename "donkey.properties"
        conf-dir (System/getenv "IPLANT_CONF_DIR")]
    (if (nil? conf-dir)
      (reset! props (cp/read-properties (file filename)))
      (reset! props (cp/read-properties (file conf-dir filename)))))
  (init-service))

(defn load-configuration-from-zookeeper
  "Loads the configuration properties from Zookeeper."
  []
  (println "zk-url =" zk-url)
  (cl/with-zk
    (zk-url)
    (when (not (cl/can-run?))
      (log/warn "THIS APPLICATION CANNOT RUN ON THIS MACHINE. SO SAYETH ZOOKEEPER.")
      (log/warn "THIS APPLICATION WILL NOT EXECUTE CORRECTLY.")
      (System/exit 1))
    (reset! props (cl/properties "donkey")))
  (init-service))

(defn site-handler [routes]
  (-> routes
      wrap-keyword-params
      wrap-lcase-params
      wrap-nested-params
      wrap-query-params))

(def app
  (site-handler donkey-routes))

(defn -main
  [& args]
  (load-configuration-from-zookeeper)
  (log/warn "Listening on" (listen-port))
  (jetty/run-jetty app {:port (listen-port)}))
