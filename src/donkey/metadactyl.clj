(ns donkey.metadactyl
  (:use [donkey.beans]
        [donkey.config])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]
           [org.iplantc.workflow.client ZoidbergClient]
           [org.iplantc.workflow.service WorkflowElementRetrievalService]
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
  (defbean zoidberg-client
    "The client used to communicate with Zoidberg services."
    (doto (ZoidbergClient.)
      (.setBaseUrl (zoidberg-base-url))
      (.setConnectionTimeout (zoidberg-connection-timeout))
      (.setEncoding (zoidberg-encoding)))))

(defn get-workflow-elements
  "A service to get information about workflow elements."
  [element-type]
  (.getElements (workflow-element-service) element-type))
