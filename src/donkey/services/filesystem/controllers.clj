(ns donkey.services.filesystem.controllers
  (:use [clojure-commons.error-codes]
        [clojure.java.classpath]
        [donkey.util.config]
        [donkey.util.validators]
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

(defn do-homedir
  "Returns the home directory for the listed user."
  [params]
  (irods-actions/user-home-dir (irods-home) (:user params) false))

(defn- get-home-dir
  [user]
  (irods-actions/user-home-dir (irods-home) user true))

(defn- top-level-listing
  "Performs a top-level directory listing."
  [params]
  (log/warn "[top-level-listing]" (:user params))
  (let [user       (:user params)
        comm-f     (future (irods-actions/list-directories user (fs-community-data)))
        share-f    (future (irods-actions/list-directories user (irods-home)))
        home-f     (future (irods-actions/list-directories user (get-home-dir user)))]
    {:roots [@home-f @comm-f @share-f]}))

(defn- shared-with-me-listing?
  [path]
  (= (utils/add-trailing-slash path) (utils/add-trailing-slash (irods-home))))

(defn do-directory
  "Performs a list-dirs command.

   Request Parameters:
     user - Query string value containing a username."
  [params]
  (cond
    (not (contains? params :path))
    (top-level-listing params)
      
    (shared-with-me-listing? (:path params))
    (irods-actions/list-directories (:user params) (irods-home))
      
    :else
    (irods-actions/list-directories (:user params) (:path params))))

(defn do-root-listing
  [params]
  (let [user           (:user params)
        uhome          (utils/path-join (irods-home) user)
        user-root-list (partial irods-actions/root-listing user)
        user-trash-dir (irods-actions/user-trash-dir user)]
    {:roots
     (remove
       nil?
       [(user-root-list uhome)
        (user-root-list (fs-community-data))
        (user-root-list (irods-home))
        (user-root-list user-trash-dir true)])}))

(defn do-rename
  "Performs a rename.

   Function Parameters:
     request - Ring request map.
     rename-func - The rename function to call.

   Request Parameters:
     user - Query string value containing a username.
     dest - JSON field from the body telling what to rename the file to.
     source - JSON field from the body telling which file to rename."
  [params body]
  (irods-actions/rename-path (:user params) (:source body) (:dest body)))

(defn do-delete
  "Performs a delete.

   Function Parameters:
     request - Ring request map.
     delete-func - The deletion function to call.

   Request Parameters:
     user - Query string value containing a username.
     paths - JSON field containing a list of paths that should be deleted."
  [params body]  
  (irods-actions/delete-paths (:user params) (:paths body)))

(defn do-move
  "Performs a move.

   Function Parameters:
     request - Ring request map.
     move-func - The move function to call.

   Request Parameters:
     user - Query string value containing a username.
     sources - JSON field containing a list of paths that should be moved.
     dest - JSON field containing the destination path."
  [params body]  
  (irods-actions/move-paths (:user params) (:sources body) (:dest body)))

(defn do-create
  "Performs a directory creation.

   Function Parameters:
     request - Ring request map.

   Request Parameters:
     user - Query string value containing a username.
     path - JSON field containing the path to create."
  [params body]
  (irods-actions/create (:user params) (:path body)))

(defn do-metadata-get
  [params]
  (irods-actions/metadata-get (:user params) (:path params)))

(defn do-metadata-set
  [params body]
  (irods-actions/metadata-set (:user params) (:path params) body))

(defn- fix-username
  [username]
  (if (re-seq #"@" username)
    (subs username 0 (.indexOf username "@"))
    username))

(defn boolean?
  [flag]
  (or (true? flag) (false? flag)))

(defn do-share
  [params body]
  (let [user        (fix-username (:user params))
        share-withs (map fix-username (:users body))]
    (irods-actions/share user share-withs (:paths body) (:permissions body))))

(defn do-unshare
  [params body]
  (let [user        (fix-username (:user params))
        share-withs (map fix-username (:users body))
        fpaths      (:paths body)]
    (irods-actions/unshare user share-withs fpaths)))

(defn do-metadata-batch-set
  [params body]
  (irods-actions/metadata-batch-set (:user params) (:path body) body))

(defn do-metadata-delete
  [params]
  (irods-actions/metadata-delete (:user params) (:path params) (:attr params)))

(defn do-preview
  "Handles a file preview.

   Request Parameters:
     user - Query string field containing a username.
     path - Query string field containing the file to preview."
  [params]
  {:preview (irods-actions/preview (:user params) (:path params) (fs-preview-size))})

(defn do-exists
  "Returns True if the path exists and False if it doesn't."
  [params body]
  {:paths
   (apply
     conj {}
     (map #(hash-map %1 (irods-actions/path-exists? (:user params) %1))
          (:paths body)))})

(defn do-stat
  "Returns data object status information for one or more paths."
  [params body]
  (let [paths (:paths body)
        user  (:user params)]
  {:paths (into {} (map #(vector % (irods-actions/path-stat user %)) paths))}))

(defn do-manifest
  "Returns a manifest consisting of preview and rawcontent fields for a
   file."
  [params]
  (irods-actions/manifest (:user params) (:path params) (fs-data-threshold)))

(defn do-download
  [params body]
  (irods-actions/download (:user params) (:paths body)))

(defn do-upload
  [params]
  (validate-map params {:user string?})
  (irods-actions/upload (:user params)))

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
  "Handles a file download

   Request Parameters:
     user - Query string field containing a username.
     path - Query string field containing the path to download."
  [params]
  (let [user (:user params)
        path (:path params)]
    (let [content      (irods-actions/download-file user path)
          content-type @(future (irods-actions/tika-detect-type user path))
          disposition  (get-disposition params)]
      {:status               200
       :body                 content
       :headers {"Content-Disposition" disposition
                 "Content-Type"        content-type}})))

(defn do-user-permissions
  [params body]
  {:paths (irods-actions/list-perms (:user params) (:paths body))})

(defn do-restore
  "Handles restoring a file or directory from a user's trash directory."
  [params body]
  (irods-actions/restore-path
    {:user  (:user params)
     :paths (:paths body)
     :user-trash (irods-actions/user-trash-dir (:user params))}))

(defn do-copy
  [params body]
  (irods-actions/copy-path
    {:user (:user params)
     :from (:paths body)
     :to   (:destination body)}
    (fs-copy-attribute)))

(defn do-groups
  [params]
  "Handles a request for the names of the groups a user belongs to.

   Request parameters:
     user - Query string field contain the iRODS account name for the user of
       interest."
  {:groups (irods-actions/list-user-groups (:user params))})

(defn do-quota
  "Handles returning a list of objects representing
   all of the quotas that a user has."
  [params]
  {:quotas (irods-actions/get-quota (:user params))})

(defn do-user-trash
  [params]
  (irods-actions/user-trash (:user params)))

(defn do-delete-trash
  [params]
  (irods-actions/delete-trash (:user params)))

(defn check-tickets
  [tickets]
  (every? true? (mapv #(= (set (keys %)) (set [:path :ticket-id])) tickets)))

(defn do-add-tickets
  [params body]
  (let [pub-param (:public params)
        public    (if (and pub-param (= pub-param "1")) true false)]
    (irods-actions/add-tickets (:user params) (:paths body) public)))

(defn do-remove-tickets
  [params body]
  (irods-actions/remove-tickets (:user params) (:tickets body)))

(defn do-list-tickets
  [params body]
  (irods-actions/list-tickets-for-paths (:user params) (:paths body)))

(defn do-paths-contain-space
  [params body]
  {:paths (irods-actions/paths-contain-char (:paths body) " ")})

(defn do-replace-spaces
  [params body]
  (let [paths (:paths body)
        user  (:user params)]
    (irods-actions/replace-spaces user paths "_")))

(defn do-read-chunk
  [params body]
  (let [user (:user params)
        path (:path body)
        pos  (Long/parseLong (:position body))
        size (Long/parseLong (:chunk-size body))]
    (irods-actions/read-file-chunk user path pos size)))

(defn do-overwrite-chunk
  [params body]
  (let [user (:user params)
        path (:path body)
        pos  (Long/parseLong (:position body))]
    (irods-actions/overwrite-file-chunk user path pos (:update body))))

(defn do-paged-listing
  [params]
  (let [user       (:user params)
        path       (:path params)
        limit      (Integer/parseInt (:limit params))
        offset     (Integer/parseInt (:offset params))
        sort-col   (if (contains? params :sort-col) (:sort-col params) "NAME")
        sort-order (if (contains? params :sort-order) (:sort-order params) "ASC")]
    (irods-actions/paged-dir-listing user path limit offset :sort-col sort-col :sort-order sort-order)))

(defn do-unsecured-paged-listing
  [params]
  (let [user       "ipctest"
        path       (:path params)
        limit      (Integer/parseInt (:limit params))
        offset     (Integer/parseInt (:offset params))
        sort-col   (if (contains? params :sort-col) (:sort-col params) "NAME")
        sort-order (if (contains? params :sort-order) (:sort-order params) "ASC")]
    (irods-actions/paged-dir-listing user path limit offset :sort-col sort-col :sort-order sort-order)))

(defn do-get-csv-page
  [params body]
  (let [user      (:user params)
        path      (:path body)
        delim     (first (:delim body))
        size      (Long/parseLong (:chunk-size body))
        page      (Long/parseLong (:page body))
        positions (mapv #(Long/parseLong %) (:page-positions body ["0"]))]
    (irods-actions/get-csv-page user path delim positions page size)))

(defn do-read-csv-chunk
  [params body]
  (let [user   (:user params)
        path   (:path body)
        ending (:line-ending body)
        sep    (:separator body)
        pos    (Long/parseLong (:position body))
        size   (Long/parseLong (:chunk-size body))]
    (irods-actions/read-csv-chunk user path pos size ending sep)))
