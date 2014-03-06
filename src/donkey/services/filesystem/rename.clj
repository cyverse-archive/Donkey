(ns donkey.services.filesystem.rename
  (:use [clojure-commons.error-codes]
        [clojure-commons.validators]
        [donkey.util.config]
        [donkey.services.filesystem.common-paths]
        [clj-jargon.init :only [with-jargon]]
        [clj-jargon.item-ops :only [move]]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.logging :as log]
            [clojure-commons.file-utils :as ft]
            [dire.core :refer [with-pre-hook! with-post-hook!]]
            [donkey.services.filesystem.validators :as validators]))

(defn rename-path
  "High-level file renaming. Calls rename-func, passing it file-rename as the mv-func param."
  [user source dest]
  (with-jargon (jargon-cfg) [cm]
    (let [source    (ft/rm-last-slash source)
          dest      (ft/rm-last-slash dest)
          src-base  (ft/basename source)
          dest-base (ft/basename dest)]
      (if (= src-base dest-base)
        {:source source :dest dest :user user}
        (do
          (validators/user-exists cm user)
          (validators/path-exists cm source)
          (validators/user-owns-path cm user source)
          (validators/path-not-exists cm dest)

          (let [result (move cm source dest :user user :admin-users (irods-admins))]
            (when-not (nil? result)
              (throw+ {:error_code ERR_INCOMPLETE_RENAME
                       :paths result
                       :user user}))
            {:source source :dest dest :user user}))))))

(defn do-rename
  [{user :user} {source :source dest :dest}]
  (rename-path user source dest))

(with-post-hook! #'do-rename (log-func "do-rename"))

(with-pre-hook! #'do-rename
  (fn [params body]
    (log-call "do-rename" params body)
    (validate-map params {:user string?})
    (validate-map body {:source string? :dest string?})
    (when (super-user? (:user params))
      (throw+ {:error_code ERR_NOT_AUTHORIZED
               :user       (:user params)}))
    (validators/validate-num-paths-under-folder (:user params) (:source body))))
