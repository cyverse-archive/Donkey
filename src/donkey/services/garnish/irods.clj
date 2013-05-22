(ns donkey.services.garnish.irods
  (:use [donkey.util.config]
        [clj-jargon.jargon]
        [clojure-commons.error-codes]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [cheshire.core :as json] 
            [hoot.rdf :as rdf]
            [hoot.csv :as csv]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure-commons.file-utils :as ft]
            [donkey.util.config :as cfg])
  (:import [org.apache.tika Tika]))

(def all-types (set (concat rdf/accepted-languages csv/csv-types)))

(defn tmp-file
  []
  (java.io.File/createTempFile "donkey-" ".kick"))

(defn chunk-input-stream
  [istream max-size]
  (let [curr-size (atom 0)]
    (proxy [java.io.InputStream] []
      (available [] (.available istream))
      (mark [readlimit] (.mark istream readlimit))
      (markSupported [] (.markSupported istream))
      (read
        ([] (if (>= @curr-size max-size) 
              -1 
              (do (reset! curr-size (inc @curr-size))
                (.read istream))))
        ([b] (if (>= @curr-size max-size)
               -1
               (do (reset! curr-size (+ @curr-size (count b)))
                 (.read istream b))))
        ([b off len] (if (>= @curr-size max-size)
                       -1
                       (do (reset! curr-size (+ @curr-size len))
                         (.read istream b off len)))))
      (reset [] (.reset istream))
      (skip [] (.skip istream))
      (close []
        (.close istream)))))

(defn copy-to-temp
  [cm path]
  (let [tmpf (tmp-file)] 
    (with-open [ins  (chunk-input-stream (input-stream cm path) (cfg/filetype-read-amount))
                outs (io/output-stream tmpf)]
      (log/info "Created temp file " (str tmpf) " containing chunk of " path " from iRODS.")
      (io/copy ins outs)
      (str tmpf))))

(defn type-from-script
  [cm path]
  (try+
    (let [tmp-path (copy-to-temp cm path)
          result   (sh/sh "perl" (cfg/filetype-script) "-f" tmp-path)
          outstr   (:out result)]
      (:ipc-media-type (json/parse-string outstr true)))
    (catch Exception e
      (log/error "Error determining type from file path:\n" (format-exception e))
      nil)))

(defn content-type
  [cm path]
  (let [script-type (type-from-script cm path)]
    (log/info "Path " path " has a type of " script-type " from the script.")
    (if (or (nil? script-type) (empty? script-type))
      (.detect (Tika.) (input-stream cm path))
      script-type)))

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
    (log/info "Added type " type " to " path " for " user ".")
    {:path path
     :type type}))

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
      (log/info "Auto-added type " type " to " path " for " user ".")
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
    
    (let [ct (content-type cm path)]
      (log/info "Preview type of " path " for " user " is " ct ".")
      {:path path
       :type (content-type cm path)})))

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
    (log/info "Deleted type " type " from " path " for " user ".")
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
    (let [path-types (get-attribute cm path (garnish-type-attribute))]
      (log/info "Retrieved types " path-types " from " path " for " user ".")
      (mapv :value path-types))))

(defn home-dir
  [cm user]
  (ft/path-join "/" (:zone cm) "home" user))
  
(defn find-paths-with-type
  [user type]
  (with-jargon (jargon-cfg) [cm]
    (when-not (user-exists? cm user)
      (throw+ {:error_code ERR_NOT_A_USER
               :user       user}))
    
    (let [paths-with-type (list-everything-in-tree-with-attr cm 
                            (home-dir cm user) 
                            {:name (garnish-type-attribute) :value type})]
      (log/info "Looked up all paths with a type of " type " for " user "\n" paths-with-type)
      paths-with-type)))

