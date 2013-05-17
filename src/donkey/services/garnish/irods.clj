(ns donkey.services.garnish.irods
  (:use [donkey.util.config]
        [clj-jargon.jargon]
        [clojure-commons.error-codes]
        [slingshot.slingshot :only [throw+]])
  (:require [hoot.rdf :as rdf]
            [hoot.csv :as csv]
            [clojure-commons.file-utils :as ft])
  (:import [org.apache.tika Tika]))

(def all-types (set (concat rdf/accepted-languages csv/csv-types)))

(defn add-type
  [user path type]
  (with-jargon (jargon-cfg) [cm]
    (when-not (contains? all-types type)
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :type type}))
    
    (when-not (exists? cm path)
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :path path}))
    
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when-not (owns? cm user path)
      (throw+ {:error_code ERR_NOT_OWNER
               :user user
               :path path}))
    
    (set-metadata cm path (garnish-type-attribute) type "")
    {:path path
     :type type}))

(defn content-type
  [cm path]
  (.detect (Tika.) (input-stream cm path)))

(defn auto-add-type
  [user path]
  (with-jargon (jargon-cfg) [cm]
    (when-not (exists? cm path)
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :path path}))
    
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when-not (owns? cm user path)
      (throw+ {:error_code ERR_NOT_OWNER
               :user user
               :path path}))
    
    (let [type (content-type cm path)] 
      (set-metadata cm path (garnish-type-attribute) type "")
      {:path path
       :type type})))

(defn preview-auto-type
  [user path]
  (with-jargon (jargon-cfg) [cm]
    (when-not (exists? cm path)
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :path path}))
    
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when-not (owns? cm user path)
      (throw+ {:error_code ERR_NOT_OWNER
               :user user
               :path path}))
    
    {:path path
     :type (content-type cm path)}))

(defn get-avus
  [cm dir-path attr val]
  "Returns a list of avu maps for set of attributes associated with dir-path"
  (validate-path-lengths dir-path)
  (filter
    #(and (= (:attr %1) attr) 
          (= (:value %1) val))
    (get-metadata cm dir-path)))

(defn delete-type
  [user path type]
  (with-jargon (jargon-cfg) [cm]
    (when-not (contains? all-types type)
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :type type}))
    (when-not (exists? cm path)
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :path path}))
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    (when-not (owns? cm user path)
      (throw+ {:error_code ERR_NOT_OWNER
               :user user
               :path path}))
    (delete-avus cm path (get-avus cm path (garnish-type-attribute) type))
    {:path path
     :type type
     :user user}))

(defn get-types
  [user path]
  (with-jargon (jargon-cfg) [cm]
    (when-not (exists? cm path)
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :path path}))
    
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when-not (is-readable? cm user path)
      (throw+ {:error_code ERR_NOT_READABLE
               :user user
               :path path}))
    (mapv :value (get-attribute cm path (garnish-type-attribute)))))

(defn home-dir
  [cm user]
  (ft/path-join "/" (:zone cm) "home" user))
  
(defn find-paths-with-type
  [user type]
  (with-jargon (jargon-cfg) [cm]
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (list-everything-in-tree-with-attr cm 
      (home-dir cm user) 
      {:name (garnish-type-attribute) :value type})))

