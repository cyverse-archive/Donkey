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

(defn- dir-list
  ([user directory include-files]
     (dir-list user directory include-files false))

  ([user directory include-files set-own?]
     (when (super-user? user)
       (throw+ {:error_code ERR_NOT_AUTHORIZED
                :user user}))

     (let [comm-dir   (fs-community-data)
           user-dir   (utils/path-join (irods-home) user)
           public-dir (utils/path-join (irods-home) "public")
           files-to-filter (conj
                            (fs-filter-files)
                            comm-dir
                            user-dir
                            public-dir)]
       (irods-actions/list-dir
        user
        directory
        include-files
        files-to-filter
        set-own?))))

(defn do-homedir
  "Returns the home directory for the listed user."
  [req-params]
  (log/debug "do-homedir")
  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string?})
    (irods-actions/user-home-dir (irods-home) (:user params) false)))

(defn- get-home-dir
  [user]
  (irods-actions/user-home-dir (irods-home) user true))

(defn- include-files?
  [params]
  (if (contains? params :includefiles)
    (not= "0" (:includefiles params))
    false))

(defn- gen-comm-data
  [user inc-files]
  (log/warn "[gen-comm-data]" user "files?:" inc-files)
  (let [cdata (if-not inc-files
                (irods-actions/list-directories user (fs-community-data))
                (dir-list user (fs-community-data) inc-files))]
    (assoc cdata :label "Community Data")))

(defn- gen-sharing-data
  [user inc-files]
  (let [comm-dir        (fs-community-data)
        user-dir        (utils/path-join (irods-home) user)
        public-dir      (utils/path-join (irods-home) "public")
        files-to-filter (conj (fs-filter-files) comm-dir user-dir public-dir)]
    (irods-actions/shared-root-listing user (irods-home) inc-files files-to-filter)))

(defn- top-level-listing
  "Performs a top-level directory listing."
  [params]
  (log/warn "[top-level-listing]" (:user params) "files?:" (include-files? params))
  (let [user       (:user params)
        inc-files  (include-files? params)
        comm-f     (future (gen-comm-data user inc-files))
        share-f    (future (gen-sharing-data user inc-files))
        home-f     (future (dir-list user (get-home-dir user) inc-files))]
    {:roots [@home-f @comm-f @share-f]}))

(defn- shared-with-me-listing?
  [path]
  (= (utils/add-trailing-slash path) (utils/add-trailing-slash (irods-home))))

(defn- filter-shared-with-me
  [user listing]
  (let [folders    (:folders listing)
        user-home  (get-home-dir user)
        swm-folder (utils/path-join (irods-home) "shared")
        pub-folder (utils/path-join (irods-home) "public")]
    (assoc listing :folders 
      (filter 
        #(not (contains? #{user-home swm-folder pub-folder} (:id %))) 
        folders))))

(defn- shared-with-me-listing
  [params]
  (let [incl-files? (include-files? params)
        user        (:user params)
        dir         (irods-home)]
    (log/warn "[shared-with-me-listing]" user "files?:" incl-files?)
    (if incl-files?
      (irods-actions/shared-root-listing user dir incl-files? [])
      (irods-actions/list-directories user dir))))

(defn- default-listing
  [params]
  (let [inc-files? (include-files? params)
        user       (:user params)
        path       (:path params)]
    (cond
      (irods-actions/user-trash-dir? user path)
      (do (log/warn "[default-listing] trash directory for " user)
        (dir-list user path inc-files? true))
      
      (false? inc-files?)
      (do (log/warn "[default-listing]" path "for" user "without files")
        (irods-actions/list-directories user path))
      
      :else
      (do (log/warn "[default-listing]" path "for" user "with files =" inc-files?)
        (dir-list user path inc-files?)))))

(defn do-directory
  "Performs a list-dirs command.

   Request Parameters:
     user - Query string value containing a username."
  [req-params]
  (log/debug "do-directory")

  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string?})

    ;;; If there's no path parameter, then it's a top-level
    ;;; request and the community listing should be included.
    (cond
      (not (contains? params :path))
      (top-level-listing params)
      
      (shared-with-me-listing? (:path params))
      (shared-with-me-listing params)
      
      :else
      (default-listing params))))

(defn do-root-listing
  [req-params]
  (log/warn "do-root-listing")
  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string?})
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
          (user-root-list user-trash-dir true)])})))

(defn do-rename
  "Performs a rename.

   Function Parameters:
     request - Ring request map.
     rename-func - The rename function to call.

   Request Parameters:
     user - Query string value containing a username.
     dest - JSON field from the body telling what to rename the file to.
     source - JSON field from the body telling which file to rename."
  [req-params req-body]
  (log/debug "do-rename")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:source string? :dest string?})

    (when (super-user? (:user params))
      (throw+ {:error_code ERR_NOT_AUTHORIZED
               :user       (:user params)}))

    (irods-actions/rename-path (:user params) (:source body) (:dest body))))

(defn do-delete
  "Performs a delete.

   Function Parameters:
     request - Ring request map.
     delete-func - The deletion function to call.

   Request Parameters:
     user - Query string value containing a username.
     paths - JSON field containing a list of paths that should be deleted."
  [req-params req-body]
  (log/debug "do-delete")
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body   {:paths sequential?})

    (when (super-user? (:user params))
      (throw+ {:error_code ERR_NOT_AUTHORIZED
               :user       (:user params)}))

    (irods-actions/delete-paths (:user params) (:paths body))))

(defn do-move
  "Performs a move.

   Function Parameters:
     request - Ring request map.
     move-func - The move function to call.

   Request Parameters:
     user - Query string value containing a username.
     sources - JSON field containing a list of paths that should be moved.
     dest - JSON field containing the destination path."
  [req-params req-body]
  (log/debug "do-move")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:sources sequential? :dest string?})

    (log/info "Body: " (json/encode body))

    (when (super-user? (:user params))
      (throw+ {:error_code ERR_NOT_AUTHORIZED
               :user (:user params)}))

    (irods-actions/move-paths (:user params) (:sources body) (:dest body))))

(defn do-create
  "Performs a directory creation.

   Function Parameters:
     request - Ring request map.

   Request Parameters:
     user - Query string value containing a username.
     path - JSON field containing the path to create."
  [req-params req-body]
  (log/debug "do-create")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:path string?})

    (log/info "Body: " body)

    (when (super-user? (:user params))
      (throw+ {:error_code ERR_NOT_AUTHORIZED :user (:user params)}))

    (irods-actions/create (:user params) (:path body))))

(defn do-metadata-get
  [req-params]
  (log/debug "do-metadata-get")

  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string? :path string?})
    (irods-actions/metadata-get (:user params) (:path params))))

(defn do-metadata-set
  [req-params req-body]
  (log/debug "do-metadata-set")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string? :path string?})
    (validate-map body {:attr string? :value string? :unit string?})
    (irods-actions/metadata-set (:user params) (:path params) body)))

(defn- fix-username
  [username]
  (if (re-seq #"@" username)
    (subs username 0 (.indexOf username "@"))
    username))

(defn boolean?
  [flag]
  (or (true? flag) (false? flag)))

(defn do-share
  [req-params req-body]
  (log/debug "do-share")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths sequential? :users sequential? :permissions map?})
    (validate-map (:permissions body) {:read boolean? :write boolean? :own boolean?})
    (let [user        (fix-username (:user params))
          share-withs (map fix-username (:users body))]
      (irods-actions/share user share-withs (:paths body) (:permissions body)))))

(defn do-unshare
  [req-params req-body]
  (log/debug "do-unshare")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths sequential? :users sequential?})
    (let [user        (fix-username (:user params))
          share-withs (map fix-username (:users body))
          fpaths      (:paths body)]
      (irods-actions/unshare user share-withs fpaths))))

(defn- check-adds
  [adds]
  (mapv #(= (set (keys %)) (set [:attr :value :unit])) adds))

(defn- check-dels
  [dels]
  (mapv string? dels))

(defn do-metadata-batch-set
  [req-params req-body]
  (log/debug "do-metadata-set")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string? :path string?})
    (validate-map body {:add sequential? :delete sequential?})

    (let [user (:user params)
          path (:path params)
          adds (:add body)
          dels (:delete body)]
      (when (pos? (count adds))
        (if (not (every? true? (check-adds adds)))
          (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :field "add"})))

      (when (pos? (count dels))
        (if-not (every? true? (check-dels dels))
          (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :field "add"})))

      (irods-actions/metadata-batch-set user path body))))

(defn do-metadata-delete
  [req-params]
  (log/debug "do-metadata-delete")
  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string? :path string? :attr string?})
    (irods-actions/metadata-delete (:user params) (:path params) (:attr params))))

(defn do-preview
  "Handles a file preview.

   Request Parameters:
     user - Query string field containing a username.
     path - Query string field containing the file to preview."
  [req-params]
  (log/debug "do-preview")

  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string? :path string?})

    (when (super-user? (:user params))
      (throw+ {:error_code ERR_NOT_AUTHORIZED
               :user (:user params)
               :path (:path params)}))
    {:preview (irods-actions/preview
                (:user params)
                (:path params)
                (fs-preview-size))}))

(defn do-exists
  "Returns True if the path exists and False if it doesn't."
  [req-params req-body]
  (log/debug "do-exists")
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths vector?})
    {:paths
     (apply
       conj {}
       (map #(hash-map %1 (irods-actions/path-exists? (:user params) %1))
            (:paths body)))}))

(defn do-stat
  "Returns data object status information for one or more paths."
  [req-params req-body]
  (log/debug "do-stat")
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths vector?})
    (let [paths (:paths body)
          user  (:user params)]
    {:paths (into {} (map #(vector % (irods-actions/path-stat user %)) paths))})))

(defn do-manifest
  "Returns a manifest consisting of preview and rawcontent fields for a
   file."
  [req-params]
  (log/debug "do-manifest")
  (let [params (add-current-user-to-map req-params)]
    (irods-actions/manifest (:user params) (:path params) (fs-data-threshold))))

(defn do-download
  [req-params req-body]
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (irods-actions/download (:user params) (:paths body))))

(defn do-upload
  [req-params]
  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string?})
    (irods-actions/upload (:user params))))

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
  [req-params]
  (log/debug "do-download")

  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string? :path string?})

    (let [user (:user params)
          path (:path params)]
      (log/info "User for download: " user)
      (log/info "Path to download: " path)

      (when (super-user? user)
        (throw+ {:error_code ERR_NOT_AUTHORIZED
                 :user       user}))
      
      (let [content      (irods-actions/download-file user path)
            content-type @(future (irods-actions/tika-detect-type user path))
            disposition  (get-disposition params)]
        {:status               200
         :body                 content
         :headers {"Content-Disposition" disposition
                   "Content-Type"        content-type}}))))

(defn do-user-permissions
  "Handles returning the list of user permissions for a file
   or directory.

   Request parameters:
      user - Query string field containing the username of the user
             making the request.
      path - Query string field containin the path to the file."
  [req-params req-body]
  (log/debug "do-user-permissions")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    {:paths (irods-actions/list-perms (:user params) (:paths body))}))

(defn do-restore
  "Handles restoring a file or directory from a user's trash directory."
  [req-params req-body]
  (log/debug "do-restore")
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (irods-actions/restore-path
      {:user  (:user params)
       :paths (:paths body)
       :user-trash (irods-actions/user-trash-dir (:user params))})))

(defn do-copy
  [req-params req-body]
  (log/debug "do-copy")
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths sequential? :destination string?})
    (irods-actions/copy-path
      {:user (:user params)
       :from (:paths body)
       :to   (:destination body)}
      (fs-copy-attribute))))

(defn do-groups
  [req-params]
  "Handles a request for the names of the groups a user belongs to.

   Request parameters:
     user - Query string field contain the iRODS account name for the user of
       interest."
  (log/debug "do-groups")
  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string?})
    {:groups (irods-actions/list-user-groups (:user params))}))

(defn do-quota
  "Handles returning a list of objects representing
   all of the quotas that a user has."
  [req-params]
  (log/debug "do-quota")
  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string?})
    {:quotas (irods-actions/get-quota (:user params))}))

(defn do-user-trash
  [req-params]
  (log/debug "do-user-trash")
  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string?})
    (irods-actions/user-trash (:user params))))

(defn do-delete-trash
  [req-params]
  (log/debug "do-delete-trash")
  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string?})
    (irods-actions/delete-trash (:user params))))

(defn check-tickets
  [tickets]
  (every? true? (mapv #(= (set (keys %)) (set [:path :ticket-id])) tickets)))

(defn do-add-tickets
  [req-params req-body]
  (log/debug "do-add-tickets")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]

    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})

    (let [pub-param (:public params)
          public    (if (and pub-param (= pub-param "1")) true false)]
      (irods-actions/add-tickets (:user params) (:paths body) public))))

(defn do-remove-tickets
  [req-params req-body]
  (log/debug "do-remove-tickets")
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:tickets sequential?})

    (when-not (every? true? (mapv string? (:tickets body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field     "tickets"}))
    (irods-actions/remove-tickets (:user params) (:tickets body))))

(defn do-list-tickets
  [req-params req-body]
  (log/debug "do-list-tickets")
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})

    (when-not (every? true? (mapv string? (:paths body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field      "paths"}))

    (irods-actions/list-tickets-for-paths (:user params) (:paths body))))

(defn do-paths-contain-space
  [req-params req-body]
  (log/debug "do-path-contain-space")
  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})

    (when-not (every? true? (mapv string? (:paths body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field      "paths"}))
    {:paths (irods-actions/paths-contain-char (:paths body) " ")}))

(defn do-replace-spaces
  [req-params req-body]
  (log/debug "do-substitute-spaces")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})

    (when-not (every? true? (mapv string? (:paths body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field      "paths"}))

    (let [paths (:paths body)
          user  (:user params)]
      (irods-actions/replace-spaces user paths "_"))))

(defn do-read-chunk
  [req-params req-body]
  (log/debug "do-read-chunk")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:path string? :position string? :chunk-size string?})

    (let [user (:user params)
          path (:path body)
          pos  (Long/parseLong (:position body))
          size (Long/parseLong (:chunk-size body))]
      (irods-actions/read-file-chunk user path pos size))))

(defn do-overwrite-chunk
  [req-params req-body]
  (log/debug "do-overwrite-chunk")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:path string? :position string? :update string?})

    (let [user (:user params)
          path (:path body)
          pos  (Long/parseLong (:position body))]
      (irods-actions/overwrite-file-chunk user path pos (:update body)))))

(defn do-paged-listing
  [req-params]
  (log/debug "do-paged-listing")

  (let [params (add-current-user-to-map req-params)]
    (validate-map params {:user string? :path string? :limit string? :offset string?})

    (let [user       (:user params)
          path       (:path params)
          limit      (Integer/parseInt (:limit params))
          offset     (Integer/parseInt (:offset params))
          sort-col   (if (contains? params :sort-col) (:sort-col params) "NAME")
          sort-order (if (contains? params :sort-order) (:sort-order params) "ASC")]
      (irods-actions/paged-dir-listing user path limit offset :sort-col sort-col :sort-order sort-order))))

(defn do-unsecured-paged-listing
  [params]
  (validate-map params {:path string? :limit string? :offset string?})

  (let [user       "ipctest"
        path       (:path params)
        limit      (Integer/parseInt (:limit params))
        offset     (Integer/parseInt (:offset params))
        sort-col   (if (contains? params :sort-col) (:sort-col params) "NAME")
        sort-order (if (contains? params :sort-order) (:sort-order params) "ASC")]
    (irods-actions/paged-dir-listing user path limit offset :sort-col sort-col :sort-order sort-order)))

(defn- validate-get-csv-page-request-body
  [body]
  (validate-map
   body
   {:path       string?
    :delim      string?
    :chunk-size string?
    :page       string?}))

(defn do-get-csv-page
  [req-params req-body]
  (log/debug "do-get-csv-page")

  (let [params    (add-current-user-to-map req-params)
        body      (parse-body (slurp req-body))
        _         (validate-map params {:user string?})
        _         (validate-get-csv-page-request-body body)
        user      (:user params)
        path      (:path body)
        delim     (first (:delim body))
        size      (Long/parseLong (:chunk-size body))
        page      (Long/parseLong (:page body))
        positions (mapv #(Long/parseLong %) (:page-positions body ["0"]))]
    (irods-actions/get-csv-page user path delim positions page size)))

(defn do-read-csv-chunk
  [req-params req-body]
  (log/debug "do-read-csv-chunk")

  (let [params (add-current-user-to-map req-params)
        body   (parse-body (slurp req-body))]
    (validate-map params {:user string?})
    (validate-map body {:path        string? 
                        :position    string? 
                        :chunk-size  string? 
                        :line-ending string? 
                        :separator   string?})

    (let [user   (:user params)
          path   (:path body)
          ending (:line-ending body)
          sep    (:separator body)
          pos    (Long/parseLong (:position body))
          size   (Long/parseLong (:chunk-size body))]
      (irods-actions/read-csv-chunk user path pos size ending sep))))
