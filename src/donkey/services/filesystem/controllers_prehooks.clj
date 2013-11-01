(ns donkey.services.filesystem.controllers-prehooks
  (:use [clojure-commons.error-codes]
        [donkey.services.filesystem.common-paths]
        [donkey.services.filesystem.validators]
        [donkey.util.validators]
        [donkey.util.config]
        [donkey.services.filesystem.controllers]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [dire.core :refer [with-pre-hook!]]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]))

(with-pre-hook! #'do-share
  (fn [params body]
    (log/warn "[call][do-share]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential? :users sequential? :permissions map?})
    (validate-map (:permissions body) {:read boolean? :write boolean? :own boolean?})
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-unshare
  (fn [params body]
    (log/warn "[call][do-unshare]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential? :users sequential?})
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-preview
  (fn [params]
    (log/warn "[call][do-preview]" params)
    (validate-map params {:user string? :path string?})
    (when (super-user? (:user params))
      (throw+ {:error_code ERR_NOT_AUTHORIZED
               :user (:user params)
               :path (:path params)}))))

(with-pre-hook! #'do-exists
  (fn [params body]
    (log/warn "[call][do-exists]" params)
    (validate-map params {:user string?})
    (validate-map body {:paths vector?})
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-stat
  (fn [params body]
    (log/warn "[call][do-stat]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths vector?})
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-manifest
  (fn [params]
    (log/warn "[call][do-manifest]" params)))

(with-pre-hook! #'do-download
  (fn [params body]
    (log/warn "[call][do-download]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-special-download
  (fn [params]
    (log/warn "[call][do-special-download] params")
    (validate-map params {:user string? :path string?})
    (let [user (:user params)
          path (:path params)]
      (log/info "User for download: " user)
      (log/info "Path to download: " path)
    
      (when (super-user? user)
        (throw+ {:error_code ERR_NOT_AUTHORIZED
                 :user       user})))))

(with-pre-hook! #'do-user-permissions
  (fn [params body]
    (log/warn "[call][do-user-permissions]" params body)    
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-copy
  (fn [params body]
    (log/warn "[call][do-copy]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential? :destination string?})
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-groups
  (fn [params]
    (log/warn "[call][do-groups]" params)
    (validate-map params {:user string?})))

(with-pre-hook! #'do-quota
  (fn [params]
    (log/warn "[call][do-quota]" params)
    (validate-map params {:user string?})))

(with-pre-hook! #'do-add-tickets
  (fn [params body]
    (log/warn "[call][do-add-tickets]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-remove-tickets
  (fn [params body]
    (log/warn "[call][do-remove-tickets]" params body)
    (validate-map params {:user string?})
    (validate-map body {:tickets sequential?})
    (when-not (every? true? (mapv string? (:tickets body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field     "tickets"}))))

(with-pre-hook! #'do-list-tickets
  (fn [params body]
    (log/warn "[call][do-list-tickets]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (when-not (every? true? (mapv string? (:paths body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field      "paths"}))
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-paths-contain-space
  (fn [params body]
    (log/warn "[call][do-path-contain-space]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (when-not (every? true? (mapv string? (:paths body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field      "paths"}))
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-replace-spaces
  (fn [params body]
    (log/warn "[call][do-substitute-spaces]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (when-not (every? true? (mapv string? (:paths body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field      "paths"}))
    (validate-num-paths (:paths body))))

(with-pre-hook! #'do-read-chunk
  (fn [params body]
    (log/warn "[call][do-read-chunk]" params body)
    (validate-map params {:user string?})
    (validate-map body {:path string? :position string? :chunk-size string?})))

(with-pre-hook! #'do-overwrite-chunk
  (fn [params body]
    (log/warn "[call][do-overwrite-chunk]" params body)
    (validate-map params {:user string?})
    (validate-map body {:path string? :position string? :update string?})))

(with-pre-hook! #'do-get-csv-page
  (fn [params body]
    (log/warn "[call][do-get-csv-page]" params body)
    (validate-map params {:user string?})
    (validate-map
      body
      {:path       string?
       :delim      string?
       :chunk-size string?
       :page       string?})))

(with-pre-hook! #'do-read-csv-chunk
  (fn [params body]
    (log/warn "[call][do-read-csv-chunk]" params body)
    (validate-map params {:user string?})
    (validate-map body {:path        string? 
                        :position    string? 
                        :chunk-size  string? 
                        :line-ending string? 
                        :separator   string?})))

(with-pre-hook! #'do-upload
  (fn [params]
    (log/warn "[call][do-upload]" params)
    (validate-map params {:user string?})))
