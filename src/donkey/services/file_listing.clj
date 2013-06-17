(ns donkey.services.file-listing
  (:use [clojure-commons.error-codes] 
        [donkey.clients.nibblonian]
        [donkey.util.config]
        [donkey.util.service :only [decode-stream required-param success-response]]
        [slingshot.slingshot :only [throw+]])
  (:require [cheshire.core :as cheshire]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [donkey.services.user-prefs :as prefs]))

(defn- generate-output-dir
  "Automatically generates the default output directory based on a default name."
  [base]
  (log/debug "generating output directory: base =" base)
  (let [path (gen-output-dir base)]
    (prefs/save-default-output-dir path)
    path))

(defn- validate-output-dir
  "Validates the user's selected output directory."
  [path]
  (log/debug "validating path:" path)
  (let [validated-path (get-or-create-dir path)]
    (when-not validated-path
      (throw+ {:error_code ERR_NOT_A_FOLDER
               :type :regular-file-selected-as-output-folder
               :path  path}))
    path))

(defn get-default-output-dir
  "Determines whether or not the default directory name exists for a user."
  []
  {:path (validate-output-dir (prefs/get-default-output-dir))})

(defn reset-default-output-dir
  "Resets the default output directory for a user."
  [body]
  (let [path (required-param (decode-stream body) :path)]
    {:path (generate-output-dir (build-path (home-dir) path))}))
