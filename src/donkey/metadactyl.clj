(ns donkey.metadactyl
  (:use [donkey.beans]
        [donkey.config])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]
           [org.iplantc.workflow.client ZoidbergClient]
           [org.iplantc.workflow.service
            AnalysisCategorizationService CategoryService ExportService
            InjectableWorkspaceInitializer PipelineService TemplateGroupService
            UserService WorkflowElementRetrievalService WorkflowExportService
            AnalysisListingService]
           [org.springframework.orm.hibernate3.annotation
            AnnotationSessionFactoryBean]))

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
  (defbean user-service
    "Services used to obtain information about a user."
    (doto (UserService.)
      (.setSessionFactory (session-factory))
      ;; TODO: replace UserSessionService with something that will work.
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
      ;; TODO: replace UserSessionService with something that will work.
      (.setUserSessionService nil))))

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
  )

(defn get-public-analyses
  "Retrieves the list of public analyses."
  []
  (.listPublicAnalyses (analysis-listing-service)))

(defn get-only-analysis-groups
  [workspace-id]
  (.listAnalysisGroups (analysis-listing-service) workspace-id))

(defn export-template
  [template-id]
  (.exportTemplate (workflow-export-service) template-id))