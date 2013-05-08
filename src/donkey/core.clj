(ns donkey.core
  (:gen-class)
  (:use [clojure.java.io :only [file]]
        [clojure-commons.lcase-params :only [wrap-lcase-params]]
        [clojure-commons.query-params :only [wrap-query-params]]
        [compojure.core]
        [donkey.buggalo]
        [donkey.file-listing]
        [donkey.metadactyl]
        [donkey.parsely]
        [donkey.service]
        [donkey.sharing]
        [donkey.user-attributes]
        [donkey.user-info]
        [donkey.user-sessions]
        [donkey.user-prefs]
        [donkey.util]
        [ring.middleware keyword-params])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.clavin-client :as cl]
            [clojure-commons.props :as cp]
            [clojure-commons.error-codes :as ce]
            [ring.adapter.jetty :as jetty]
            [donkey.config :as config]
            [donkey.jex :as jex]
            [donkey.search :as search]
            [donkey.parsely :as parsely])
  (:import [java.util UUID]))

(defn secured-routes
  []
  (routes
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

   (DELETE "/notifications/delete-all" [:as {params :params}]
           (trap #(delete-all-notifications params)))

   (POST "/notifications/seen" [:as req]
         (trap #(mark-notifications-as-seen req)))

   (POST "/notifications/mark-all-seen" [:as req]
         (trap #(mark-all-notifications-seen req)))

   (GET "/notifications/system/messages" [:as req]
        (trap #(get-system-messages req)))

   (GET "/notifications/system/unseen-messages" [:as req]
        (trap #(get-unseen-system-messages req)))

   (POST "/notifications/system/seen" [:as req]
         (trap #(mark-system-messages-seen req)))

   (POST "/notifications/system/mark-all-seen" [:as req]
         (trap #(mark-all-system-messages-seen req)))

   (POST "/notifications/system/delete" [:as req]
         (trap #(delete-system-messages req)))

   (DELETE "/notifications/system/delete-all" [:as req]
           (trap #(delete-all-system-messages req)))

   (PUT "/notifications/admin/system" [:as req]
        (trap #(admin-add-system-message req)))

   (GET "/notifications/admin/system/:uuid" [uuid :as req]
        (trap #(admin-get-system-message req uuid)))

   (POST "/notifications/admin/system/:uuid" [uuid :as req]
         (trap #(admin-update-system-message req uuid)))

   (DELETE "/notifications/admin/system/:uuid" [uuid :as req]
           (trap #(admin-delete-system-message req uuid)))

   (GET "/notifications/admin/system-types" [:as req]
        (trap #(admin-list-system-types req)))

   (GET "/template/:app-id" [app-id :as req]
        (trap #(get-app-secured req app-id)))

   (GET "/app/:app-id" [app-id :as req]
        (trap #(get-app-new-format req app-id)))

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

   (GET "/edit-app/:app-id" [app-id :as req]
        (trap #(edit-app-new-format req app-id)))

   (GET "/edit-workflow/:app-id" [app-id :as req]
        (trap #(edit-workflow req app-id)))

   (GET "/copy-template/:app-id" [app-id :as req]
        (trap #(copy-app req app-id)))

   (GET "/copy-workflow/:app-id" [app-id :as req]
        (trap #(copy-workflow req app-id)))

   (PUT "/update-template" [:as req]
        (trap #(update-template-secured req)))

   (PUT "/update-app" [:as req]
        (trap #(update-app-secured req)))

   (POST "/make-analysis-public" [:as req]
         (trap #(make-app-public req)))

   (GET "/sessions" []
        (trap user-session))

   (POST "/sessions" [:as {body :body}]
         (trap #(user-session (slurp body))))

   (DELETE "/sessions" []
           (trap remove-session))

   (GET "/preferences" []
        (trap user-prefs))

   (POST "/preferences" [:as {body :body}]
         (trap #(user-prefs (slurp body))))

   (DELETE "/preferences" []
           (trap remove-prefs))

   (GET "/search-history" []
        (trap search-history))

   (POST "/search-history" [:as {body :body}]
         (trap #(search-history (slurp body))))

   (DELETE "/search-history" []
           (trap clear-search-history))

   (GET "/user-search/:search-string" [search-string :as req]
        (trap #(user-search search-string (get-in req [:headers "range"]))))

   (GET "/user-info" [:as {params :params}]
        (trap #(user-info (as-vector (:username params)))))

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

   (PUT "/tool-request" [:as req]
        (trap #(submit-tool-request req)))

   (POST "/tool-request" [:as req]
         (trap #(update-tool-request-secured req)))

   (GET "/tool-requests" [:as req]
        (trap #(list-tool-requests req)))

   (GET "/triples" [:as req]
        (trap #(triples req (:params req))))

   (PUT "/feedback" [:as {body :body}]
        (trap #(provide-user-feedback body)))

   (GET "/parsely/triples" [:as req]
        (trap #(parsely/triples req (:params req))))

   (GET "/parsely/type" [:as req]
        (trap #(parsely/get-types req (:params req))))

   (POST "/parsely/type" [:as req]
         (trap #(parsely/add-type req (:params req))))

   (GET "/parsely/type/paths" [:as req]
        (trap #(parsely/find-typed-paths req (:params req))))

   (route/not-found (unrecognized-path-response))))

(defn  donkey-routes
  []
  (routes
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

   (GET "/analysis-details/:app-id" [app-id :as req]
        (trap #(get-app-details req app-id)))

   (GET "/get-only-analysis-groups/:workspace-id" [workspace-id :as req]
        (trap #(get-only-analysis-groups req workspace-id)))

   (GET "/list-analysis/:app-id" [app-id :as req]
        (trap #(list-app req app-id)))

   (GET "/export-template/:template-id" [template-id :as req]
        (trap #(export-template req template-id)))

   (GET "/export-app/:app-id" [app-id :as req]
        (trap #(export-app req app-id)))

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

   (POST "/update-app-labels" [:as req]
         (trap #(update-app-labels req)))

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

   (GET "/analysis-rerun-info/:job-id" [job-id :as req]
        (trap #(get-app-rerun-info req job-id)))

   (GET "/app-rerun-info/:job-id" [job-id :as req]
        (trap #(get-new-app-rerun-info req job-id)))

   (POST "/send-notification" [:as req]
         (trap #(send-notification req)))

   (POST "/tree-viewer-urls" [:as {body :body}]
         (trap #(tree-viewer-urls-for body)))

   (GET "/uuid" []
        (string/upper-case (str (UUID/randomUUID))))

   (POST "/tool-request" [:as req]
         (trap #(update-tool-request req)))

   (GET "/tool-request/:uuid" [uuid :as req]
        (trap #(get-tool-request req uuid)))

   (POST "/arg-preview" [:as req]
         (trap #(preview-args req)))

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

(defn site-handler [routes-fn]
  (-> (routes-fn)
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
