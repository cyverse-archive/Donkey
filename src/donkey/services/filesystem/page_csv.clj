(ns donkey.services.filesystem.page-csv
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
            [donkey.services.filesystem.validators :as validators]
            [deliminator.core :as deliminator])
  (:import [au.com.bytecode.opencsv CSVReader]))

(defn- read-csv
  [separator csv-str]
  (let [ba  (java.io.ByteArrayInputStream. (.getBytes csv-str))
        isr (java.io.InputStreamReader. ba "UTF-8")]
    (mapv #(zipmap (mapv str (range (count %1))) %1) 
          (mapv vec (.readAll (CSVReader. isr (.charAt separator 0)))))))

(defn- read-csv-chunk
   "Reads a chunk of a file and parses it as a CSV. The position and chunk-size are not guaranteed, since
   we shouldn't try to parse partial rows. We scan forward from the starting position to find the first
   line-ending and then scan backwards from the last position for the last line-ending."
   [user path position chunk-size separator]
   (with-jargon (jargon-cfg) [cm]
     (log/warn "[read-csv-chunk]" user path position chunk-size separator)
     (validators/user-exists cm user)
     (validators/path-exists cm path)
     (validators/path-is-file cm path)
     (validators/path-readable cm user path)
     
     (when-not (< position (- (file-size cm path) 1))
       (throw+ {:error_code "ERR_POSITION_TOO_FAR"
                :position   (str position)
                :file-size  (str (file-size cm path))}))
     
     (let [chunk   (read-at-position cm path position chunk-size)
           the-csv (read-csv separator chunk)]
       {:path       path
        :user       user
        :max-cols   (str (reduce #(if (>= %1 %2) %1 %2) (map count the-csv)))
        :start      (str position)
        :end        (str (- (+ position chunk-size) 1))
        :chunk-size (str chunk-size)
        :file-size  (str (file-size cm path))
        :csv        the-csv})))

(with-pre-hook! #'read-csv-chunk
  (fn [user path position chunk-size separator]
    (when-not (>= position 0)
      (throw+ {:error_code "ERR_POSITION_NOT_POS"
               :position   (str position)}))
    (when-not (pos? chunk-size)
      (throw+ {:error_code "ERR_CHUNK_TOO_SMALL"
               :chunk-size (str chunk-size)}))
    (when (string/blank? separator)
      (throw+ {:error_code "ERR_SEPARATOR_BLANK"}))))

(defn do-read-csv-chunk
  [{user :user} 
   {path :path 
    separator   :separator 
    position    :position 
    chunk-size  :chunk-size}]
  (let [pos    (Long/parseLong position)
        size   (Long/parseLong chunk-size)]
    (read-csv-chunk user path pos size separator)))

(with-pre-hook! #'do-read-csv-chunk
  (fn [params body]
    (log/warn "[call][do-read-csv-chunk]" params body)
    (validate-map params {:user string?})
    (validate-map body {:path        string? 
                        :position    string? 
                        :chunk-size  string?  
                        :separator   string?})))

(with-post-hook! #'do-read-csv-chunk (log-func "do-read-csv-chunk"))
