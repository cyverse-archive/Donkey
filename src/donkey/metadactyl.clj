(ns donkey.metadactyl
  (:use [clojure-commons.cas-proxy-auth :only (validate-cas-proxy-ticket)]
        [donkey.beans]
        [donkey.config]
        [donkey.notifications]
        [donkey.service]
        [donkey.transformers])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]
           [java.util HashMap]
           [org.iplantc.authn.service UserSessionService]
           [org.iplantc.authn.user User]
           [org.iplantc.workflow.client OsmClient ZoidbergClient]
           [org.iplantc.files.service FileInfoService]
           [org.iplantc.files.types
            FileTypeHandler ReferenceAnnotationHandler ReferenceGenomeHandler
            ReferenceSequenceHandler]
           [org.iplantc.workflow HibernateTemplateFetcher]
           [org.iplantc.workflow.experiment
            AnalysisRetriever AnalysisService ExperimentRunner
            IrodsUrlAssembler]
           [org.iplantc.workflow.service
            AnalysisCategorizationService AnalysisEditService CategoryService
            ExportService InjectableWorkspaceInitializer PipelineService
            TemplateGroupService UserService WorkflowElementRetrievalService
            WorkflowExportService AnalysisListingService WorkflowPreviewService
            WorkflowImportService AnalysisDeletionService RatingService]
           [org.iplantc.workflow.template.notifications NotificationAppender]
           [org.springframework.orm.hibernate3.annotation
            AnnotationSessionFactoryBean])
  (:require [clojure.tools.logging :as log]))

(def
  ^{:doc "The authenticated user or nil if the service is unsecured."
    :dynamic true}
   current-user nil)

(def
  ^{:doc "The service used to get information about the authenticated user."}
   user-session-service (proxy [UserSessionService] []
                          (getUser [] current-user)))

(defn- user-from-attributes
  "Creates an instance of org.iplantc.authn.user.User from user attributes
   stored in the request by validate-cas-proxy-ticket."
  [{:keys [user-attributes]}]
  (doto (User.)
    (.setUsername (str (:uid user-attributes) "@" (uid-domain)))
    (.setPassword (:password user-attributes))
    (.setEmail (:email user-attributes))
    (.setShortUsername (:uid user-attributes))))

(defn store-current-user
  "Authenticates the user using validate-cas-proxy-ticket and binds
   current-user to a new instance of org.iplantc.authn.user.User that is built
   from the user attributes that validate-cas-proxy-ticket stores in the
   request."
  [handler cas-server server-name]
  (validate-cas-proxy-ticket
    (fn [request]
      (binding [current-user (user-from-attributes request)]
        (handler request)))
    cas-server server-name))

(register-bean
  (defbean db-url
    "The URL to use when connecting to the database."
    (str "jdbc:" (db-subprotocol) "://" (db-host) ":" (db-port) "/" (db-name))))

(register-bean
  (defbean data-source
    "The data source used to obtain database connections."
    (doto (ComboPooledDataSource.)
      (.setDriverClass (db-driver-class))
      (.setJdbcUrl (db-url))
      (.setUser (db-user))
      (.setPassword (db-password)))))

(register-bean
  (defbean session-factory
    "A factory for generating Hibernate sessions."
    (.getObject
      (doto (AnnotationSessionFactoryBean.)
        (.setDataSource (data-source))
        (.setPackagesToScan (into-array String (hibernate-packages)))
        (.setMappingResources (into-array String (hibernate-resources)))
        (.setHibernateProperties (as-properties
                                   {"hibernate.dialect" (hibernate-dialect)
                                    "hibernate.hbm2ddl.auto" "validate"
                                    "hibernate.jdbc.batch-size" "50"}))
        (.afterPropertiesSet)))))

(register-bean
  (defbean workflow-element-service
    "Services used to obtain elements that are commonly shared by multiple
     apps in the metadata model (for example, property types)."
    (doto (WorkflowElementRetrievalService.)
      (.setSessionFactory (session-factory)))))

(register-bean
  (defbean workflow-export-service
    "Services used to export apps and templates from the DE."
    (WorkflowExportService. (session-factory))))

(register-bean
  (defbean export-service
    "Services used to determine whether or not an ap can be exported."
    (doto (ExportService.)
      (.setSessionFactory (session-factory)))))

(register-bean
  (defbean category-service
    "Services used to manage app categories."
    (doto (CategoryService.)
      (.setSessionFactory (session-factory)))))

(register-bean
  (defbean pipeline-service
    "Services used to manage pipelines"
    (doto (PipelineService.)
      (.setSessionFactory (session-factory)))))

(register-bean
  (defbean zoidberg-client
    "The client used to communicate with Zoidberg services."
    (doto (ZoidbergClient.)
      (.setBaseUrl (zoidberg-base-url))
      (.setConnectionTimeout (zoidberg-connection-timeout))
      (.setEncoding (zoidberg-encoding)))))

(register-bean
  (defbean osm-client
    "The client used to communicate with OSM services."
    (doto (OsmClient.)
      (.setBaseUrl (osm-base-url))
      (.setBucket (osm-jobs-bucket))
      (.setConnectionTimeout (osm-connection-timeout))
      (.setEncoding (osm-encoding)))))

(register-bean
  (defbean user-service
    "Services used to obtain information about a user."
    (doto (UserService.)
      (.setSessionFactory (session-factory))
      (.setUserSessionService user-session-service)
      (.setRootAnalysisGroup (workspace-root-app-group))
      (.setDefaultAnalysisGroups (workspace-default-app-groups)))))

(register-bean
  (defbean workspace-initializer
    "A bean that can be used to initialize a user's workspace."
    (doto (InjectableWorkspaceInitializer.)
      (.setUserService (user-service)))))

(register-bean
  (defbean analysis-categorization-service
    "Services used to categorize apps."
    (doto (AnalysisCategorizationService.)
      (.setSessionFactory (session-factory))
      (.setDevAnalysisGroupIndex (workspace-dev-app-group-index))
      (.setFavoritesAnalysisGroupIndex (workspace-favorites-app-group-index))
      (.setWorkspaceInitializer (workspace-initializer)))))

(register-bean
  (defbean analysis-listing-service
    "Services used to list analyses."
    (doto (AnalysisListingService.)
      (.setSessionFactory (session-factory))
      (.setFavoritesAnalysisGroupIndex (workspace-favorites-app-group-index))
      (.setWorkspaceInitializer (workspace-initializer)))))

(register-bean
  (defbean template-group-service
    "Services used to place apps in app groups."
    (doto (TemplateGroupService.)
      (.setSessionFactory (session-factory))
      (.setZoidbergClient (zoidberg-client))
      (.setUserSessionService user-session-service))))

(register-bean
  (defbean reference-genome-handler
    "Resolves paths to named reference genomes."
    (doto (ReferenceGenomeHandler.)
      (.setReferenceGenomeUrlMap (reference-genomes)))))

(register-bean
  (defbean reference-sequence-handler
    "Resolves paths to named reference sequences."
    (doto (ReferenceSequenceHandler.)
      (.setReferenceGenomeUrlMap (reference-genomes)))))

(register-bean
  (defbean reference-annotation-handler
    "Resolves paths to named reference annotations."
    (doto (ReferenceAnnotationHandler.)
      (.setReferenceGenomeUrlMap (reference-genomes)))))

(def
  ^{:doc "A placeholder service used to clearly indicate that automatically
          saved barcode files are no longer supported."}
   barcode-file-handler
  (proxy [FileTypeHandler] []
    (getFileAccessUrl [file-id]
      (let [msg "barcode selectors are no longer supported"]
        (throw (IllegalArgumentException. msg))))))

(register-bean
  (defbean file-type-handlers
    "Maps property types to file type handlers."
    (doto (HashMap.)
      (.put "ReferenceGenome" (reference-genome-handler))
      (.put "ReferenceAnnotation" (reference-annotation-handler))
      (.put "ReferenceSequence" (reference-sequence-handler))
      (.put "BarcodeSelector" barcode-file-handler))))

(register-bean
  (defbean workflow-preview-service
    "Handles workflow/metadactyl related previews."
    (WorkflowPreviewService. (session-factory) (reference-genome-handler))))

(register-bean
  (defbean workflow-import-service
    "Handles workflow/metadactyl import actions."
    (WorkflowImportService. 
      (session-factory) 
      (Integer/toString (workspace-dev-app-group-index)) 
      (Integer/toString (workspace-favorites-app-group-index)) 
      (workspace-initializer))))

(register-bean
  (defbean analysis-deletion-service
    "Handles workflow/metadactyl deletion actions."
    (AnalysisDeletionService. (session-factory))))

(register-bean
  (defbean app-fetcher
    "Retrieves apps from the database."
    (doto (HibernateTemplateFetcher.)
      (.setSessionFactory (session-factory))
      (.setRefGenomeHandler (reference-genome-handler)))))

(register-bean
  (defbean notification-appender
    "Appends UI notifications to an app."
    (doto (NotificationAppender.)
      (.setSessionFactory (session-factory)))))

(register-bean
  (defbean analysis-edit-service
    "Services to make apps available for editing in Tito."
    (doto (AnalysisEditService.)
      (.setSessionFactory (session-factory))
      (.setZoidbergClient (zoidberg-client))
      (.setUserService (user-service))
      (.setWorkflowExportService (workflow-export-service)))))

(register-bean
  (defbean analysis-retriever
    "Used by several services to retrieve apps from the daatabase."
    (doto (AnalysisRetriever.)
      (.setSessionFactory (session-factory)))))

(register-bean
  (defbean rating-service
    "Services to associate user ratings with or remove user ratings from
     apps."
    (doto (RatingService.)
      (.setSessionFactory (session-factory))
      (.setUserSessionService user-session-service)
      (.setAnalysisRetriever (analysis-retriever)))))

(register-bean
  (defbean analysis-service
    "Services to retrieve information about analyses that a user has
     submitted."
    (doto (AnalysisService.)
      (.setSessionFactory (session-factory))
      (.setOsmBaseUrl (osm-base-url))
      (.setOsmBucket (osm-jobs-bucket))
      (.setConnectionTimeout (osm-connection-timeout)))))

(register-bean
  (defbean file-info-service
    "Services used to resolve paths to named files."
    (doto (FileInfoService.)
      (.setFileTypeHandlerMap (file-type-handlers)))))

(register-bean
  (defbean url-assembler
    "Used to assemble URLs."
    (IrodsUrlAssembler.)))

(register-bean
  (defbean experiment-runner
    "Services to submit jobs to the JEX for execution."
    (doto (ExperimentRunner.)
      (.setSessionFactory (session-factory))
      (.setFileInfo (file-info-service))
      (.setUserService (user-service))
      (.setExecutionUrl (jex-base-url))
      (.setUrlAssembler (url-assembler))
      (.setOsmClient (osm-client)))))

(defn- notificationagent-url
  "Builds a URL that can be used to connect to the notification agent."
  [relative-url]
  (build-url (notificationagent-base-url) relative-url))

(defn get-workflow-elements
  "A service to get information about workflow elements."
  [element-type]
  (.getElements (workflow-element-service) element-type))

(defn get-all-app-ids
  "A service to get the list of app identifiers."
  []
  (.getAnalysisIds (workflow-export-service)))

(defn delete-categories
  "A service used to delete app categories."
  [body]
  (.deleteCategories (category-service) (slurp body)))

(defn validate-app-for-pipelines
  "A service used to determine whether or not an app can be included in a
   pipeline."
  [app-id]
  (.validateAnalysisForPipelines (pipeline-service) app-id))

(defn get-data-objects-for-app
  "A service used to list the data objects in an app."
  [app-id]
  (.getDataObjectsForAnalysis (pipeline-service) app-id))

(defn categorize-apps
  "A service used to recategorize apps."
  [body]
  (.categorizeAnalyses (analysis-categorization-service) (slurp body)))

(defn get-app-categories
  "A service used to get a list of app categories."
  [category-set]
  (.getAnalysisCategories (analysis-categorization-service) category-set))

(defn can-export-app
  "A service used to determine whether or not an app can be exported to Tito."
  [body]
  (.canExportAnalysis (export-service) (slurp body)))

(defn add-app-to-group
  "A service used to add an existing app to an app group."
  [body]
  (.addAnalysisToTemplateGroup (template-group-service) (slurp body)))

(defn get-app
  "A service used to get an app in the format required by the DE."
  [app-id]
  (.appendNotificationToTemplate (notification-appender)
    (.fetchTemplateByName (app-fetcher) app-id)))

(defn get-public-analyses
  "Retrieves the list of public analyses."
  []
  (.listPublicAnalyses (analysis-listing-service)))

(defn get-only-analysis-groups
  "Retrieves the list of public analyses."
  [workspace-id]
  (.listAnalysisGroups (analysis-listing-service) workspace-id))

(defn export-template
  "This service will export the template with the given identifier."
  [template-id]
  (.exportTemplate (workflow-export-service) template-id))

(defn export-workflow
  "This service will export a workflow with the given identifier."
  [app-id]
  (.exportAnalysis (workflow-export-service) app-id))

(defn preview-template
  "This service will convert a JSON document in the format consumed by 
   the import service into the format required by the DE."
  [body]
  (.previewTemplate (workflow-preview-service) (slurp body)))

(defn preview-workflow
  "This service will convert a JSON document in the format consumed by 
   the import service into the format required by the DE."
  [body]
  (.previewWorkflow (workflow-preview-service) (slurp body)))

(defn import-template
  "This service will import a template into the DE."
  [body]
  (.importTemplate (workflow-import-service) (slurp body))
  (empty-response))

(defn import-workflow
  "This service will import a workflow into the DE."
  [body]
  (.importWorkflow (workflow-import-service) (slurp body))
  (empty-response))

(defn update-template
  "This service will either update an existing template or import a new template."
  [body]
  (.updateTemplate (workflow-import-service) (slurp body))
  (empty-response))

(defn update-workflow
  "This service will either update an existing workflow or import a new workflow."
  [body]
  (.updateWorkflow (workflow-import-service) (slurp body))
  (empty-response))

(defn force-update-workflow
  "This service will either update an existing workflow or import a new workflow.  
   Vetted workflows may be updated."
  [body]
  (.forceUpdateWorkflow (workflow-import-service) (slurp body))
  (empty-response))

(defn delete-workflow
  "This service will logically remove a workflow from the DE."
  [body]
  (.deleteAnalysis (analysis-deletion-service) (slurp body))
  (empty-response))

(defn permanently-delete-workflow
  "This service will physically remove a workflow from the DE."
  [body]
  (.physicallyDeleteAnalysis (analysis-deletion-service) (slurp body))
  (empty-response))

(defn bootstrap
  "This service obtains information about and initializes the workspace for
   the authenticated user."
  []
  (object->json (.getCurrentUserInfo (user-service))))

(defn get-messages
  "This service forwards requests to the notification agent in order to
   retrieve notifications that the user may or may not have seen yet."
  [req]
  (let [url (notificationagent-url "get-messages")]
    (add-app-details
      (forward-post url req (add-username-to-json req))
      (analysis-retriever))))

(defn get-unseen-messages
  "This service forwards requests to the notification agent in order to
   retrieve notifications that the user hasn't seen yet."
  [req]
  (let [url (notificationagent-url "get-unseen-messages")]
    (add-app-details
      (forward-post url req (add-username-to-json req))
      (analysis-retriever))))

(defn delete-notifications
  "This service forwards requests to the notification agent in order to delete
   existing notifications."
  [req]
  (let [url (notificationagent-url "delete")]
    (forward-post url req (add-username-to-json req))))

(defn run-experiment
  "This service accepts a job submission from a user then reformats it and
   submits it to the JEX."
  [body]
  (.runExperiment (experiment-runner) (object->json (slurp body))))

(defn get-experiments
  "This service retrieves information about jobs that a user has submitted."
  [workspace-id]
  (.retrieveExperimentsByWorkspaceId
    (analysis-service) (string->long workspace-id)))

(defn delete-experiments
  "This service marks experiments as deleted so that they no longer show up
   in the Analyses window."
  [body]
  (.deleteExecutionSet (analysis-service) (slurp body))
  (empty-response))

(defn rate-app
  "This service adds a user's rating to an app."
  [body]
  (.rateAnalysis (rating-service) (slurp body)))

(defn delete-rating
  "This service removes a user's rating from an app."
  [body]
  (.deleteRating (rating-service) (slurp body)))

(defn list-apps-in-group
  "This service lists all of the apps in an app group and all of its
   descendents."
  [app-group-id]
  (.listAnalysesInGroup (analysis-listing-service) app-group-id))

(defn update-favorites
  "This service adds apps to or removes apps from a user's favorites list."
  [body]
  (.updateFavorite (analysis-categorization-service) (slurp body)))

(defn edit-app
  "This service makes an app available in Tito for editing."
  [app-id]
  (.prepareAnalysisForEditing (analysis-edit-service) app-id))

(defn copy-app
  "This service makes a copy of an app available in Tito for editing."
  [app-id]
  (.copyAnalysis (analysis-edit-service) app-id))

(defn make-app-public
  "This service copies an app from a user's private workspace to the public
   workspace."
  [body]
  (.makeAnalysisPublic (template-group-service) (slurp body)))
