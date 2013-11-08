(ns donkey.services.filesystem.page-csv
  (:use [clojure-commons.error-codes] 
        [donkey.util.config]
        [donkey.util.validators]
        [donkey.services.filesystem.common-paths]
        [donkey.services.filesystem.validators]
        [clj-jargon.init :only [with-jargon]]
        [clj-jargon.item-info :only [file-size]]
        [clj-jargon.paging :only [read-at-position]]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.logging :as log]
            [cemerick.url :as url]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]
            [cheshire.core :as json]
            [dire.core :refer [with-pre-hook! with-post-hook!]]
            [donkey.services.filesystem.validators :as validators]
            [deliminator.core :as deliminator])
  (:import [au.com.bytecode.opencsv CSVReader]))

(def line-ending "\n")

(defn- trim-to-line-start
  [str-chunk]
  (let [line-pos (+ (.indexOf str-chunk line-ending) 1)]
    
    (cond
      (<= line-pos 1) 
      str-chunk
      
      (>= line-pos (- (count str-chunk) 1))
      ""
      
      :else
      (.substring str-chunk line-pos))))

(defn- trim-front
  [position chunk]
  (if-not (pos? position) 
    chunk 
    (trim-to-line-start chunk)))

(defn- calc-start-pos
  "Calculates the new start position after (trim-to-line-start) has been called."
  [start-pos orig-chunk trimmed-chunk]
  (+ start-pos (- (count (.getBytes orig-chunk)) (count (.getBytes trimmed-chunk)))))

(defn- trim-to-last-line
  [str-chunk]
  (let [calced-pos (log/spy (- (.lastIndexOf str-chunk line-ending) 1))
        last-pos   (log/spy (if-not (pos? calced-pos) 0 calced-pos))]
    (.substring str-chunk 0 last-pos)))

(defn- trim-back
  [path position file-size chunk front-trimmed-chunk]
  (log/warn front-trimmed-chunk)
  (if (>= (+ position (- (count (.getBytes chunk)) 1)) 
          (- file-size 1)) 
    front-trimmed-chunk
    (log/spy (trim-to-last-line front-trimmed-chunk))))

(defn- calc-end-pos
  "Calculates the new ending byte based on the start position and the current size of the chunk."
  [start-pos trimmed-chunk]
  (+ start-pos (- (count (.getBytes trimmed-chunk)) 1)))

(defn- read-csv
  [separator csv-str]
  (if-not (string/blank? csv-str)
    (let [ba  (java.io.ByteArrayInputStream. (.getBytes csv-str))
          isr (java.io.InputStreamReader. ba "UTF-8")]
      (mapv #(zipmap (mapv str (range (count %1))) %1) 
            (mapv vec (.readAll (CSVReader. isr (.charAt separator 0))))))
    [{}]))

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
    (let [chunk         (read-at-position cm path position chunk-size)
          front-trimmed (trim-front position chunk)
          new-start-pos (calc-start-pos position chunk front-trimmed)
          trimmed-chunk (trim-back path position (file-size cm path) chunk front-trimmed)
          new-end-pos   (calc-end-pos position trimmed-chunk)
          the-csv       (read-csv separator trimmed-chunk)]
      {:path       path
       :user       user
       :max-cols   (str (reduce #(if (>= %1 %2) %1 %2) (map count the-csv)))
       :start      (str new-start-pos)
       :end        (str new-end-pos)
       :chunk-size (str (count (.getBytes trimmed-chunk)))
       :file-size  (str (file-size cm path))
       :csv        the-csv})))

(with-pre-hook! #'read-csv-chunk
  (fn [user path position chunk-size separator]
    (when-not (>= position 0)
      (throw+ {:error_code "ERR_POSITION_NOT_POS"
               :position   (str position)}))
    (when-not (pos? chunk-size)
      (throw+ {:error_code "ERR_CHUNK_TOO_SMALL"
               :chunk-size (str chunk-size)}))))

(defn do-read-csv-chunk
  [{user :user} 
   {path :path 
    separator :separator 
    position :position 
    chunk-size :chunk-size}]
  (let [pos    (Long/parseLong position)
        size   (Long/parseLong chunk-size)]
    (read-csv-chunk user path pos size (url/url-decode separator))))

(with-pre-hook! #'do-read-csv-chunk
  (fn [params body]
    (log/warn "[call][do-read-csv-chunk]" params body)
    (validate-map params {:user string?})
    (validate-map body {:path        string? 
                        :position    string? 
                        :chunk-size  string? 
                        :separator   string?})))

(with-post-hook! #'do-read-csv-chunk (log-func "do-read-csv-chunk"))
