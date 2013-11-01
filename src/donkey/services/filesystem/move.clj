(ns donkey.services.filesystem.move
  (:use [clojure-commons.error-codes] 
        [donkey.util.config]
        [donkey.util.validators]
        [donkey.services.filesystem.common-paths]
        [donkey.services.filesystem.validators]
        [clj-jargon.jargon]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]
            [cheshire.core :as json]
            [dire.core :refer [with-pre-hook! with-post-hook!]]
            [donkey.services.filesystem.validators :as validators]))

(defn- source->dest
  [source-path dest-path]
  (ft/path-join dest-path (ft/basename source-path)))

(defn- move-paths
  "Moves directories listed in 'sources' into the directory listed in 'dest'. This
   works by calling move and passing it move-dir."
  [user sources dest]
  (with-jargon (jargon-cfg) [cm]
    (let [path-list  (conj sources dest)
          all-paths  (apply merge (mapv #(hash-map (source->dest %1 dest) %1) sources))
          dest-paths (keys all-paths)
          sources    (mapv ft/rm-last-slash sources)
          dest       (ft/rm-last-slash dest)]
      (validators/user-exists cm user)
      (validators/all-paths-exist cm sources)
      (validators/all-paths-exist cm [dest])
      (validators/path-is-dir cm dest)
      (validators/user-owns-paths cm user sources)
      (validators/path-writeable cm user dest)
      (validators/no-paths-exist cm dest-paths)
      (move-all cm sources dest :user user :admin-users (irods-admins))
      {:sources sources :dest dest})))

(defn do-move
  [{user :user} {sources :sources dest :dest}]  
  (move-paths user sources dest))

(with-pre-hook! #'do-move
  (fn [params body]
    (log/warn "[call][do-move]" params body)
    (validate-map params {:user string?})
    (validate-map body {:sources sequential? :dest string?})
    (log/info "Body: " (json/encode body))
    (when (super-user? (:user params))
      (throw+ {:error_code ERR_NOT_AUTHORIZED
               :user (:user params)}))))

(with-post-hook! #'do-move (log-func "do-move"))

