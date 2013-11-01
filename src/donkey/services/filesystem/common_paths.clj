(ns donkey.services.filesystem.common-paths
  (:use [donkey.util.config]
        [clj-jargon.jargon])
  (:require [clojure-commons.file-utils :as ft]
            [clojure.set :as set]))

(defn string-contains?
  [container-str str-to-check]
  (pos? (count (set/intersection (set (seq container-str)) (set (seq str-to-check))))))

(defn good-string?
  [str-to-check]
  (not (string-contains? (fs-filter-chars) str-to-check)))

(defn valid-file-map? [map-to-check] (good-string? (:id map-to-check)))

(defn valid-path? [path-to-check] (good-string? path-to-check))

(defn sharing?
  [abs]
  (= (ft/rm-last-slash (irods-home))
     (ft/rm-last-slash abs)))

(defn community? [abs] (= (fs-community-data) abs))

(defn user-trash-path
  ([user]
     (with-jargon (jargon-cfg) [cm]
       (user-trash-path cm user)))
  ([cm user]
     (trash-base-dir cm user)))

(defn user-trash-dir?
  ([user path-to-check]
     (with-jargon (jargon-cfg) [cm]
       (user-trash-dir? cm user path-to-check)))
  ([cm user path-to-check]
     (= (ft/rm-last-slash path-to-check)
        (ft/rm-last-slash (user-trash-path cm user)))))

(defn in-trash?
  [cm user fpath]
  (.startsWith fpath (user-trash-path cm user)))

(defn date-mod-from-stat
  [stat]
  (str (long (.. stat getModifiedAt getTime))))

(defn date-created-from-stat
  [stat]
  (str (long (.. stat getCreatedAt getTime))))

(defn size-from-stat
  [stat]
  (str (.getObjSize stat)))

(defn id->label
  "Generates a label given a listing ID (read as absolute path)."
  [cm user id]
  (cond
   (user-trash-dir? cm user id)
   "Trash"

   (sharing? (ft/add-trailing-slash id))
   "Shared With Me"

   (community? id)
   "Community Data"

   :else
   (ft/basename id)))