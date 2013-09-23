(ns donkey.routes.filesystem
  (:use [compojure.core]
        [donkey.auth.user-attributes]
        [donkey.services.filesystem.controllers]
        [donkey.util])
  (:require [donkey.util.config :as config]
            [clojure.tools.logging :as log]))

(defn secured-filesystem-routes
  "The routes for file IO endpoints."
  []
  (optional-routes
    [config/filesystem-routes-enabled]
    (GET "/filesystem/root" [:as req]
         (trap #(do-root-listing (:params req))))
    
    (GET "/filesystem/home" [:as req]
         (trap #(do-homedir (:params req))))
    
    (POST "/filesystem/exists" [:as req]
          (trap #(do-exists (:params req) (:body req))))
    
    (POST "/filesystem/stat" [:as req]
          (trap #(do-stat (:params req) (:body req))))
    
    (POST "/filesystem/download" [:as req]
          (trap #(do-download (:params req) (:body req))))
    
    (GET "/filesystem/display-download" [:as req]
         (trap #(do-special-download (:params req))))
    
    (GET "/filesystem/upload" [:as req]
         (trap #(do-upload (:params req))))
    
    (GET "/filesystem/directory" [:as req]
         (trap #(do-directory (:params req))))
    
    (GET "/filesystem/paged-directory" [:as req]
         (trap #(do-paged-listing (:params req))))
    
    (POST "/filesystem/directory/create" [:as req]
          (trap #(do-create (:params req) (:body req))))
    
    (POST "/filesystem/rename" [:as req]
          (trap #(do-rename (:params req) (:body req))))
    
    (POST "/filesystem/delete" [:as req]
          (trap #(do-delete (:params req) (:body req))))
    
    (POST "/filesystem/move" [:as req]
          (trap #(do-move (:params req) (:body req))))
    
    (GET "/filesystem/file/preview" [:as req]
         (trap #(do-preview (:params req))))
    
    (GET "/filesystem/file/manifest" [:as req]
         (trap #(do-manifest (:params req))))
    
    (GET "/filesystem/metadata" [:as req]
         (trap #(do-metadata-get (:params req))))
    
    (POST "/filesystem/metadata" [:as req]
          (trap #(do-metadata-set (:params req) (:body req))))
    
    (DELETE "/filesystem/metadata" [:as req]
            (trap #(do-metadata-delete (:params req))))
    
    (POST "/filesystem/metadata-batch" [:as req]
          (trap #(do-metadata-batch-set (:params req) (:body req))))
    
    (POST "/filesystem/share" [:as req]
          (trap #(do-share (:params req) (:body req))))
    
    (POST "/filesystem/unshare" [:as req]
          (trap #(do-unshare (:params req) (:body req))))
    
    (POST "/filesystem/user-permissions" [:as req]
          (trap  #(do-user-permissions (:params req) (:body req))))
    
    (GET "/filesystem/groups" [:as req]
         (trap #(do-groups (:params req))))
    
    (GET "/filesystem/quota" [:as req]
         (trap #(do-quota (:params req))))
    
    (POST "/filesystem/restore" [:as req]
          (trap #(do-restore (:params req) (:body req))))
    
    (POST "/filesystem/tickets" [:as req]
          (trap #(do-add-tickets (:params req) (:body req))))
    
    (POST "/filesystem/delete-tickets" [:as req]
          (trap #(do-remove-tickets (:params req) (:body req))))
    
    (POST "/filesystem/list-tickets" [:as req]
          (trap #(do-list-tickets (:params req) (:body req))))
    
    (GET "/filesystem/user-trash-dir" [:as req]
         (trap #(do-user-trash (:params req))))
    
    (POST "/filesystem/paths-contain-space" [:as req]
          (trap #(do-paths-contain-space (:params req) (:body req))))
    
    (POST "/filesystem/replace-spaces" [:as req]
          (trap #(do-replace-spaces (:params req) (:body req))))
    
    (DELETE "/filesystem/trash" [:as req]
            (trap #(do-delete-trash (:params req))))
    
    (POST "/filesystem/read-chunk" [:as req]
          (trap #(do-read-chunk (:params req) (:body req))))
    
    (POST "/filesystem/overwrite-chunk" [:as req]
          (trap #(do-overwrite-chunk (:params req) (:body req))))))
