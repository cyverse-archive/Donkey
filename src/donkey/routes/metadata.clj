(ns donkey.routes.metadata
  (:use [compojure.core]
        [donkey.services.file-listing]
        [donkey.services.metadata.metadactyl]
        [donkey.util.service]
        [donkey.util])
  (:require [donkey.util.config :as config]
            [donkey.services.metadata.app-listings :as app-listings]
            [donkey.services.jex :as jex]))

(defn secured-metadata-routes
  []
  (optional-routes
   [config/metadata-routes-enabled]

   (GET "/bootstrap" [:as req]
        (trap #(bootstrap req)))

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

   (GET "/app-groups" []
        (trap #(app-listings/get-only-app-groups)))

   (GET "/get-analyses-in-group/:app-group-id" [app-group-id :as req]
        (trap #(app-listings/apps-in-group app-group-id)))

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

   (GET "/default-output-dir" []
        (trap #(get-default-output-dir)))

   (POST "/default-output-dir" [:as {body :body}]
         (trap #(reset-default-output-dir body)))

   (GET "/reference-genomes" [:as req]
        (trap #(list-reference-genomes req)))

   (PUT "/reference-genomes" [:as req]
        (trap #(replace-reference-genomes req)))

   (PUT "/tool-request" [:as req]
        (trap #(submit-tool-request req)))

   (POST "/tool-request" [:as req]
         (trap #(update-tool-request-secured req)))

   (GET "/tool-requests" [:as req]
        (trap #(list-tool-requests req)))

   (PUT "/feedback" [:as {body :body}]
        (trap #(provide-user-feedback body)))))

(defn unsecured-metadata-routes
  []
  (optional-routes
   [config/metadata-routes-enabled]

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

   (GET "/public-app-groups" [req]
        (trap #(get-public-app-groups req)))

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

   (POST "/submit-tool-request" [:as req]
         (trap #(submit-tool-request req)))

   (POST "/tool-request" [:as req]
         (trap #(update-tool-request req)))

   (GET "/tool-request/:uuid" [uuid :as req]
        (trap #(get-tool-request req uuid)))

   (POST "/arg-preview" [:as req]
         (trap #(preview-args req)))))
