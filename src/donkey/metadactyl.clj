(ns donkey.metadactyl
  (:use [clojure.data.json :only [read-json]]
        [donkey.beans]
        [donkey.config]
        [donkey.service]
        [donkey.transformers]
        [donkey.user-attributes])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]
           [java.util HashMap]
           [org.iplantc.workflow.client OsmClient]
           [org.iplantc.files.service FileInfoService]
           [org.iplantc.files.types
            FileTypeHandler ReferenceAnnotationHandler ReferenceGenomeHandler
            ReferenceSequenceHandler]
           [org.iplantc.workflow.experiment
            AnalysisRetriever AnalysisService ExperimentRunner
            IrodsUrlAssembler]
           [org.iplantc.workflow.service
            UserService]
           [org.springframework.orm.hibernate3.annotation
            AnnotationSessionFactoryBean])
  (:require [clojure.tools.logging :as log]
            [donkey.notifications :as dn]))

(defn build-metadactyl-secured-url
  "Adds the name and email of the currently authenticated user to the secured
   metadactyl URL with the given relative URL path."
  [relative-url]
  (add-current-user-to-url
    (build-url (metadactyl-base-url) relative-url)))

(defn build-metadactyl-unprotected-url
  "Builds the unsecured metadactyl URL from the given relative URL path."
  [relative-url]
  (build-url (metadactyl-unprotected-base-url) relative-url))

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
  (defbean osm-job-request-client
    "The client used to communicate with OSM services."
    (doto (OsmClient.)
      (.setBaseUrl (osm-base-url))
      (.setBucket (osm-job-request-bucket))
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
  (defbean analysis-retriever
    "Used by several services to retrieve apps from the daatabase."
    (doto (AnalysisRetriever.)
      (.setSessionFactory (session-factory)))))

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
      (.setJobRequestOsmClient (osm-job-request-client)))))

(defn get-workflow-elements
  "A service to get information about workflow elements."
  [req element-type]
  (let [url (build-metadactyl-unprotected-url
              (str "/get-workflow-elements/" element-type))]
    (forward-get url req)))

(defn search-deployed-components
  "A service to search information about deployed components."
  [req search-term]
  (let [url (build-metadactyl-unprotected-url
              (str "/search-deployed-components/" search-term))]
    (forward-get url req)))

(defn get-all-app-ids
  "A service to get the list of app identifiers."
  [req]
  (let [url (build-metadactyl-unprotected-url "/get-all-analysis-ids")]
    (forward-get url req)))

(defn delete-categories
  "A service used to delete app categories."
  [req]
  (let [url (build-metadactyl-unprotected-url "/delete-categories")]
    (forward-post url req)))

(defn validate-app-for-pipelines
  "A service used to determine whether or not an app can be included in a
   pipeline."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url
              (str "/validate-analysis-for-pipelines/" app-id))]
    (forward-get url req)))

(defn get-data-objects-for-app
  "A service used to list the data objects in an app."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url
              (str "/analysis-data-objects/" app-id))]
    (forward-get url req)))

(defn categorize-apps
  "A service used to recategorize apps."
  [req]
  (let [url (build-metadactyl-unprotected-url "/categorize-analyses")]
    (forward-post url req)))

(defn get-app-categories
  "A service used to get a list of app categories."
  [req category-set]
  (let [url (build-metadactyl-unprotected-url
              (str "/get-analysis-categories/" category-set))]
    (forward-get url req)))

(defn can-export-app
  "A service used to determine whether or not an app can be exported to Tito."
  [req]
  (let [url (build-metadactyl-unprotected-url "/can-export-analysis")]
    (forward-post url req)))

(defn add-app-to-group
  "A service used to add an existing app to an app group."
  [req]
  (let [url (build-metadactyl-unprotected-url "/add-analysis-to-group")]
    (forward-post url req)))

(defn get-app
  "A service used to get an app in the format required by the DE."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url (str "/get-analysis/" app-id))]
    (forward-get url req)))

(defn get-app-secured
  "A secured service used to get an app in the format required by the DE."
  [req app-id]
  (let [url (build-metadactyl-secured-url (str "/template/" app-id))]
    (forward-get url req)))

(defn get-only-analysis-groups
  "Retrieves the list of public analyses."
  [req workspace-id]
  (let [url (build-metadactyl-unprotected-url
              (str "/get-only-analysis-groups/" workspace-id))]
    (forward-get url req)))

(defn export-template
  "This service will export the template with the given identifier."
  [req template-id]
  (let [url (build-metadactyl-unprotected-url
              (str "/export-template/" template-id))]
    (forward-get url req)))

(defn export-workflow
  "This service will export a workflow with the given identifier."
  [req app-id]
  (let [url (build-metadactyl-unprotected-url
              (str "/export-workflow/" app-id))]
    (forward-get url req)))

(defn export-deployed-components
  "This service will export all or selected deployed components."
  [req]
  (let [url (build-metadactyl-unprotected-url "/export-deployed-components")]
    (forward-post url req)))

(defn preview-template
  "This service will convert a JSON document in the format consumed by 
   the import service into the format required by the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "/preview-template")]
    (forward-post url req)))

(defn preview-workflow
  "This service will convert a JSON document in the format consumed by 
   the import service into the format required by the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "/preview-workflow")]
    (forward-post url req)))

(defn import-template
  "This service will import a template into the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "/import-template")]
    (forward-post url req)))

(defn import-workflow
  "This service will import a workflow into the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "/import-workflow")]
    (forward-post url req)))

(defn import-tools
  "This service will import deployed components into the DE and send
   notifications if notification information is included and the deployed
   components are successfully imported."
  [req]
  (let [json-str (slurp (:body req))
        json-obj (read-json json-str)
        url (build-metadactyl-unprotected-url "/import-tools")]
    (forward-post url req json-str)
    (dorun (map #(dn/send-tool-notification %) (:components json-obj))))
  (success-response))

(defn update-template
  "This service will either update an existing template or import a new template."
  [req]
  (let [url (build-metadactyl-unprotected-url "/update-template")]
    (forward-post url req)))

(defn update-workflow
  "This service will either update an existing workflow or import a new workflow."
  [req]
  (let [url (build-metadactyl-unprotected-url "/update-workflow")]
    (forward-post url req)))

(defn force-update-workflow
  "This service will either update an existing workflow or import a new workflow.  
   Vetted workflows may be updated."
  [req]
  (let [url (build-metadactyl-unprotected-url "/force-update-workflow")]
    (forward-post url req)))

(defn delete-workflow
  "This service will logically remove a workflow from the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "/delete-workflow")]
    (forward-post url req)))

(defn permanently-delete-workflow
  "This service will physically remove a workflow from the DE."
  [req]
  (let [url (build-metadactyl-unprotected-url "/permanently-delete-workflow")]
    (forward-post url req)))

(defn bootstrap
  "This service obtains information about and initializes the workspace for
   the authenticated user."
  [req]
  (let [url (build-metadactyl-secured-url "/bootstrap")]
    (forward-get url req)))

(defn get-messages
  "This service forwards requests to the notification agent in order to
   retrieve notifications that the user may or may not have seen yet."
  [req]
  (let [url (dn/notificationagent-url "get-messages")]
    (dn/add-app-details
      (forward-post url req (add-username-to-json req))
      (analysis-retriever))))

(defn get-unseen-messages
  "This service forwards requests to the notification agent in order to
   retrieve notifications that the user hasn't seen yet."
  [req]
  (let [url (dn/notificationagent-url "get-unseen-messages")]
    (dn/add-app-details
      (forward-post url req (add-username-to-json req))
      (analysis-retriever))))

(defn delete-notifications
  "This service forwards requests to the notification agent in order to delete
   existing notifications."
  [req]
  (let [url (dn/notificationagent-url "delete")]
    (forward-post url req (add-username-to-json req))))

(defn send-notification
  "This service forwards a notifiction to the notification agent's general
   notification endpoint."
  [req]
  (let [url (dn/notificationagent-url "notification")]
    (forward-post url req)))

(defn run-experiment
  "This service accepts a job submission from a user then reformats it and
   submits it to the JEX."
  [body workspace-id]
  (let [json-str (add-workspace-id (slurp body) workspace-id)
        json-obj (object->json-obj json-str)]
    (.runExperiment (experiment-runner) json-obj))
  (empty-response))

(defn get-experiments
  "This service retrieves information about jobs that a user has submitted."
  [req workspace-id]
  (let [url (build-metadactyl-secured-url
              (str "/workspaces/" workspace-id "/executions/list"))]
    (forward-get url req)))

(defn delete-experiments
  "This service marks experiments as deleted so that they no longer show up
   in the Analyses window."
  [body workspace-id]
  (let [json-str (add-workspace-id (slurp body) workspace-id)]
    (.deleteExecutionSet (analysis-service) (object->json-obj json-str)))
  (empty-response))

(defn rate-app
  "This service adds a user's rating to an app."
  [req]
  (let [url (build-metadactyl-secured-url "/rate-analysis")]
    (forward-post url req)))

(defn delete-rating
  "This service removes a user's rating from an app."
  [req]
  (let [url (build-metadactyl-secured-url "/delete-rating")]
    (forward-post url req)))

(defn search-apps
  "This service searches for apps based on a search term."
  [req search-term]
  (let [url (build-metadactyl-secured-url
              (str "/search-analyses/" search-term))]
    (forward-get url req)))

(defn list-apps-in-group
  "This service lists all of the apps in an app group and all of its
   descendents."
  [req app-group-id]
  (let [url (build-metadactyl-secured-url
              (str "/get-analyses-in-group/" app-group-id))]
    (forward-get url req)))

(defn update-favorites
  "This service adds apps to or removes apps from a user's favorites list."
  [req]
  (let [url (build-metadactyl-secured-url "/update-favorites")]
    (forward-post url req)))

(defn edit-app
  "This service makes an app available in Tito for editing."
  [req app-id]
  (let [url (build-metadactyl-secured-url
              (str "/edit-template/" app-id))]
    (forward-get url req)))

(defn copy-app
  "This service makes a copy of an app available in Tito for editing."
  [req app-id]
  (let [url (build-metadactyl-secured-url
              (str "/copy-template/" app-id))]
    (forward-get url req)))

(defn make-app-public
  "This service copies an app from a user's private workspace to the public
   workspace."
  [req]
  (let [url (build-metadactyl-secured-url "/make-analysis-public")]
    (forward-post url req)))

(defn get-property-values
  "Gets the property values for a previously submitted job."
  [req job-id]
  (let [url (build-metadactyl-unprotected-url
              (str "/get-property-values/" job-id))]
    (forward-get url req)))
