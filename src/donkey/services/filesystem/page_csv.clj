(ns donkey.services.filesystem.page-csv
  (:use [clojure-commons.error-codes]
        [clojure-commons.validators]
        [donkey.util.config]
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

(defn- chunk-start
  [page chunk-size]
  (if (= page 0)
    0
    (dec chunk-size)))

(defn- seek-prev-line
  [chunk start-pos]
  (loop [pos start-pos]
    (let [start-str (nth chunk pos)]
      (if-not (or (= pos 0)
                  (= start-str (.charAt line-ending 0)))
        (recur (dec pos))
        (if (= pos 0) 0 (inc pos))))))

(defn- end-pos
  [chunk page pages]
  (if (= page (dec pages))
    (dec (.length chunk))
    (seek-prev-line chunk (dec (.length chunk)))))

(defn- trim-chunk
  [chunk chunk-size page pages]
  (let [lstart (seek-prev-line chunk (chunk-start page chunk-size))
        lend   (end-pos chunk page pages)]
    (.substring chunk lstart lend)))

(defn- fix-record
  [record]
  (into {}
    (map (fn [[k v]] (if (nil? v) [k ""] [k v]))
         (seq record))))

(defn- read-csv
  [csv-str separator]
  (if-not (string/blank? csv-str)
    (let [ba  (java.io.ByteArrayInputStream. (.getBytes csv-str))
          isr (java.io.InputStreamReader. ba "UTF-8")]
      (map fix-record (mapv #(zipmap (mapv str (range (count %1))) %1)
                           (mapv vec (.readAll (CSVReader. isr (.charAt separator 0)))))))
    [{}]))

(defn num-pages
  [chunk-size file-size]
  (int (Math/ceil (double (/ file-size chunk-size)))))

(defn start-pos
  [page chunk-size]
  (if (= page 0) 0 (* chunk-size page)))

(defn- calc-chunk-size
  [page chunk-size]
  (if (= page 0)
    chunk-size
    (* chunk-size 2)))

(defn- read-csv-chunk
  "Reads a chunk of a file and parses it as a CSV. The position and chunk-size are not guaranteed, since
   we shouldn't try to parse partial rows. We scan forward from the starting position to find the first
   line-ending and then scan backwards from the last position for the last line-ending."
  [user path page chunk-size separator]
  (with-jargon (jargon-cfg) [cm]
    (log/warn "[read-csv-chunk]" user path page chunk-size separator)
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-is-file cm path)
    (validators/path-readable cm user path)
    (let [page            (dec (Integer/parseInt page))
          start-pg        (if (= page 0) 0 (dec page))
          full-chunk-size (calc-chunk-size page chunk-size)
          fsize           (file-size cm path)
          pages           (num-pages chunk-size fsize)
          position        (start-pos page chunk-size)
          load-pos        (start-pos start-pg chunk-size)]

      (when-not (<= page pages)
        (throw+ {:error_code   "ERR_INVALID_PAGE"
                 :page         (str page)
                 :number-pages (str pages)}))

      (let [chunk   (trim-chunk (read-at-position cm path load-pos full-chunk-size) chunk-size page pages)
            the-csv (read-csv chunk separator)]
        {:path         path
         :page         (str (inc page))
         :number-pages (str pages)
         :user         user
         :max-cols     (str (reduce #(if (>= %1 %2) %1 %2) (map count the-csv)))
         :chunk-size   (str (count (.getBytes chunk)))
         :file-size    (str fsize)
         :csv          the-csv}))))

(defn integer-string?
  [str-to-check]
  (try
    (Integer/parseInt str-to-check)
    true
    (catch Exception e false)))

(with-pre-hook! #'read-csv-chunk
  (fn [user path page chunk-size separator]
    (when-not (integer-string? page)
      (throw+ {:error_code "ERR_PAGE_NOT_INT"
               :page       page}))
    (when-not (>= (Integer/parseInt page) 0)
      (throw+ {:error_code "ERR_PAGE_NOT_POS"
               :page       page}))
    (when-not (pos? chunk-size)
      (throw+ {:error_code "ERR_CHUNK_TOO_SMALL"
               :chunk-size (str chunk-size)}))))

(defn do-read-csv-chunk
  [{user :user}
   {path :path
    separator  :separator
    page       :page
    chunk-size :chunk-size}]
  (let [size (Long/parseLong chunk-size)]
    (read-csv-chunk user path page size (url/url-decode separator))))

(with-pre-hook! #'do-read-csv-chunk
  (fn [params body]
    (log-call "do-read-csv-chunk" params body)
    (validate-map params {:user string?})
    (validate-map body {:path        string?
                        :page        string?
                        :chunk-size  string?
                        :separator   string?})))

(with-post-hook! #'do-read-csv-chunk
  (fn [result]
    (log-result "do-read-csv-chunk" (dissoc result :csv))))

;;; Make sure that page exists
;;; Make sure that integer
;;; TODO: Make sure that page is an integer
