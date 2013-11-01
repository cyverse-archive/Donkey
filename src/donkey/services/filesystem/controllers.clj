(ns donkey.services.filesystem.controllers
  (:use [clojure-commons.error-codes]
        [clojure.java.classpath]
        [donkey.util.config]
        [donkey.util.validators]
        [donkey.services.filesystem.common-paths]
        [donkey.util.transformers :only [add-current-user-to-map]]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [cheshire.core :as json]
            [clj-jargon.jargon :as jargon]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.clavin-client :as cl]
            [clojure-commons.file-utils :as utils]
            [clojure-commons.props :as prps]
            [donkey.services.filesystem.actions :as irods-actions]
            [ring.util.codec :as cdc]
            [ring.util.response :as rsp-utils]))

(defn super-user?
  [username]
  (.equals username (irods-user)))

(defn do-rename
  [{user :user} {source :source dest :dest}]
  (irods-actions/rename-path user source dest))

(defn do-delete
  [{user :user} {paths :paths}]  
  (irods-actions/delete-paths user paths))

(defn do-move
  [{user :user} {sources :sources dest :dest}]  
  (irods-actions/move-paths user sources dest))

(defn do-create
  [{user :user} {path :path}]
  (irods-actions/create user path))

(defn do-metadata-get
  [{user :user path :path}]
  (irods-actions/metadata-get user path))

(defn do-metadata-set
  [{user :user path :path} body]
  (irods-actions/metadata-set user path body))

(defn- fix-username
  [username]
  (if (re-seq #"@" username)
    (subs username 0 (.indexOf username "@"))
    username))

(defn boolean?
  [flag]
  (or (true? flag) (false? flag)))

(defn do-share
  [{user :user} {users :users paths :paths permissions :permissions}]
  (let [user        (fix-username user)
        share-withs (map fix-username users)]
    (irods-actions/share user share-withs paths permissions)))

(defn do-unshare
  [{user :user} {users :users paths :paths}]
  (let [user        (fix-username user)
        share-withs (map fix-username users)
        fpaths      paths]
    (irods-actions/unshare user share-withs fpaths)))

(defn do-metadata-batch-set
  [{user :user} {path :path :as body}]
  (irods-actions/metadata-batch-set user path body))

(defn do-metadata-delete
  [{user :user path :path attr :attr}]
  (irods-actions/metadata-delete user path attr))

(defn do-preview
  [{user :user path :path}]
  {:preview (irods-actions/preview user path (fs-preview-size))})

(defn do-exists
  [{user :user} {paths :paths}]
  {:paths
   (apply
     conj {}
     (map #(hash-map %1 (irods-actions/path-exists? user %1)) paths))})

(defn do-stat
  [{user :user} {paths :paths}]
  {:paths (into {} (map #(vector % (irods-actions/path-stat user %)) paths))})

(defn do-manifest
  [{user :user path :path}]
  (irods-actions/manifest user path (fs-data-threshold)))

(defn do-download
  [{user :user} {paths :paths}]
  (irods-actions/download user paths))

(defn do-upload
  [{user :user}]
  (irods-actions/upload user))

(defn attachment?
  [params]
  (if-not (contains? params :attachment)
    true
    (if (= "1" (:attachment params)) true false)))

(defn- get-disposition
  [params]
  (cond
    (not (contains? params :attachment))
    (str "attachment; filename=\"" (utils/basename (:path params)) "\"")
    
    (not (attachment? params))
    (str "filename=\"" (utils/basename (:path params)) "\"")
    
    :else
    (str "attachment; filename=\"" (utils/basename (:path params)) "\"")))

(defn do-special-download
  [{user :user path :path :as params}]
  (let [content      (irods-actions/download-file user path)
        content-type @(future (irods-actions/tika-detect-type user path))
        disposition  (get-disposition params)]
    {:status               200
     :body                 content
     :headers {"Content-Disposition" disposition
               "Content-Type"        content-type}}))

(defn do-user-permissions
  [{user :user} {paths :paths}]
  {:paths (irods-actions/list-perms user paths)})

(defn do-restore
  [{user :user} {paths :paths}]
  (irods-actions/restore-path
    {:user  user
     :paths paths
     :user-trash (user-trash-path user)}))

(defn do-copy
  [{user :user} {paths :paths destination :destination}]
  (irods-actions/copy-path
    {:user user
     :from paths
     :to   destination}
    (fs-copy-attribute)))

(defn do-groups
  [{user :user}]
  {:groups (irods-actions/list-user-groups user)})

(defn do-quota
  [{user :user}]
  {:quotas (irods-actions/get-quota user)})

(defn do-user-trash
  [{user :user}]
  (irods-actions/user-trash user))

(defn do-delete-trash
  [{user :user}]
  (irods-actions/delete-trash user))

(defn check-tickets
  [tickets]
  (every? true? (mapv #(= (set (keys %)) (set [:path :ticket-id])) tickets)))

(defn do-add-tickets
  [{public :public user :user} {paths :paths}]
  (let [pub-param public
        public    (if (and public (= public "1")) true false)]
    (irods-actions/add-tickets user paths public)))

(defn do-remove-tickets
  [{user :user} {tickets :tickets}]
  (irods-actions/remove-tickets user tickets))

(defn do-list-tickets
  [{user :user} {paths :paths}]
  (irods-actions/list-tickets-for-paths user paths))

(defn do-paths-contain-space
  [params {paths :paths}]
  {:paths (irods-actions/paths-contain-char paths " ")})

(defn do-replace-spaces
  [{user :user} {paths :paths}]
  (irods-actions/replace-spaces user paths "_"))

(defn do-read-chunk
  [{user :user} {path :path position :position chunk-size :chunk-size}]
  (let [pos  (Long/parseLong position)
        size (Long/parseLong chunk-size)]
    (irods-actions/read-file-chunk user path pos size)))

(defn do-overwrite-chunk
  [{user :user} {path :path position :position update :update}]
  (let [pos  (Long/parseLong position)]
    (irods-actions/overwrite-file-chunk user path pos update)))

(defn do-get-csv-page
  [{user :user} {path :path delim :delim chunk-size :chunk-size page :page :as body}]
  (let [delim     (first delim)
        size      (Long/parseLong chunk-size)
        page      (Long/parseLong page)
        positions (mapv #(Long/parseLong %) (:page-positions body ["0"]))]
    (irods-actions/get-csv-page user path delim positions page size)))

(defn do-read-csv-chunk
  [{user :user} 
   {path :path 
    line-ending :line-ending 
    separator :separator 
    position :position 
    chunk-size :chunk-size}]
  (let [pos    (Long/parseLong position)
        size   (Long/parseLong chunk-size)]
    (irods-actions/read-csv-chunk user path pos size line-ending separator)))
