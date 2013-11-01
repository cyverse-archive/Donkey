(ns donkey.services.filesystem.actions
  (:require [cemerick.url :as url]
            [cheshire.core :as json]
            [clojure-commons.file-utils :as ft]
            [clojure.data.codec.base64 :as b64]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.java.io :as ds]
            [deliminator.core :as deliminator]
            [donkey.services.filesystem.riak :as riak]
            [donkey.services.filesystem.validators :as validators]
            [donkey.services.garnish.irods :as filetypes]
            [ring.util.codec :as cdc]
            [clj-jargon.lazy-listings :as ll]
            [clj-icat-direct.icat :as icat])
  (:use [clj-jargon.jargon :exclude [init list-dir] :as jargon]
        [clojure-commons.error-codes]
        [donkey.util.config]
        [donkey.services.filesystem.common-paths]
        [slingshot.slingshot :only [try+ throw+]])
  (:import [org.apache.tika Tika]
           [au.com.bytecode.opencsv CSVReader]
           [java.util UUID]))

(defmacro log-rulers
  [cm users msg & body]
  `(let [result# (do ~@body)]
     (when (debug-ownership)
       (->> ~users
            (map #(when (jargon/one-user-to-rule-them-all? ~cm %)
                    (jargon/log-stack-trace (str ~msg " - " % " rules all"))))
            (dorun)))
     result#))

(defn format-call
  [fn-name & args]
  (with-open [w (java.io.StringWriter.)]
    (clojure.pprint/write (conj args (symbol fn-name)) :stream w)
    (str w)))

(defn- format-tree-urls
  [treeurl-maps]
  (if (pos? (count treeurl-maps))
    (json/decode (:value (first (seq treeurl-maps))) true)
    []))

(defn read-file-chunk
  "Reads a chunk of a file starting at 'position' and reading a chunk of length 'chunk-size'."
  [user path position chunk-size]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "read-file-chunk" user path position chunk-size)
     (validators/user-exists cm user)
     (validators/path-exists cm path)
     (validators/path-is-file cm path)
     (validators/path-readable cm user path)

     {:path       path
      :user       user
      :start      (str position)
      :chunk-size (str chunk-size)
      :file-size  (str (file-size cm path))
      :chunk      (read-at-position cm path position chunk-size)})))

(defn overwrite-file-chunk
  "Writes a chunk of a file starting at 'position' and extending to the length of the string."
  [user path position update-string]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "overwrite-file-chunk" user path position update-string)
     (validators/user-exists cm user)
     (validators/path-exists cm path)
     (validators/path-is-file cm path)
     (validators/path-writeable cm user path)
     (overwrite-at-position cm path position update-string)
     {:path       path
      :user       user
      :start      (str position)
      :chunk-size (str (count (.getBytes update-string)))
      :file-size  (str (file-size cm path))})))

(defn- closest-page
  [page-positions page-number]
  (let [idx (dec page-number)
        len (count page-positions)]
    (if (<= page-number len)
      [(page-positions idx) page-number]
      [(last page-positions) len])))

(defn- csv-page-result
  [path user delim file-size chunk-size page-positions page csv]
  {:path           path
   :user           user
   :delim          (str delim)
   :file-size      (str file-size)
   :chunk-size     (str chunk-size)
   :page-positions (mapv str page-positions)
   :page           (str page)
   :csv            csv})

(defn get-csv-page
  "Retrieves a CSV page for a given chunk size. `delim` is the character that is used as a field
   separator in the file. `page-positions` is a vector of positions of pages within the file,
   which is used as an optimization when retrieving a CSV page. Without it, it would be necessary
   to sequentially scan for the requested page with every call. `page-number` is the requsted page
   number. `chunk-size` is the maximum size of a page."
  [user path delim page-positions page-number chunk-size]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-is-file cm path)
    (validators/path-readable cm user path)

    (let [size       (file-size cm path)
          get-chunk  (fn [pos] (read-at-position cm path pos chunk-size))
          parse-page (fn [chunk] (deliminator/parse-excerpt chunk delim))
          get-page   (comp parse-page get-chunk)
          add-pos    (fn [ps p] (if (> p (last ps)) (conj ps p) ps))
          build-res  (partial csv-page-result path user delim size chunk-size)]
      (loop [[pos page] (closest-page page-positions page-number)
             positions  page-positions
             [csv len]  (get-page pos)]
        (let [next-pos  (+ pos len)
              positions (add-pos positions next-pos)]
          (cond (= page page-number) (build-res positions page csv)
                (< next-pos size)    (recur [next-pos (inc page)] positions (get-page next-pos))
                :else                (build-res positions page csv)))))))

(defn trim-to-line-start
  [str-chunk line-ending]
  (let [line-pos (.indexOf str-chunk line-ending)]
    (if (<= line-pos 0)
      str-chunk
      (.substring str-chunk (+ line-pos 1)))))

(defn calc-start-pos
  "Calculates the new start position after (trim-to-line-start) has been called."
  [start-pos orig-chunk trimmed-chunk]
  (+ start-pos (- (count (.getBytes orig-chunk)) (count (.getBytes trimmed-chunk)))))

(defn trim-to-last-line
  [str-chunk line-ending]
  (let [calced-pos (- (.lastIndexOf str-chunk line-ending) 1)
        last-pos   (if-not (pos? calced-pos) 1 calced-pos)]
    (.substring str-chunk 0 last-pos)))

(defn calc-end-pos
  "Calculates the new ending byte based on the start position and the current size of the chunk."
  [start-pos trimmed-chunk]
  (+ start-pos (- (count (.getBytes trimmed-chunk)) 1)))

(defn read-csv
  [separator csv-str]
  (let [ba  (java.io.ByteArrayInputStream. (.getBytes csv-str))
        isr (java.io.InputStreamReader. ba "UTF-8")]
    (mapv vec (.readAll (CSVReader. isr (.charAt separator 0))))))

(defn read-csv-chunk
  "Reads a chunk of a file and parses it as a CSV. The position and chunk-size are not guaranteed, since
   we shouldn't try to parse partial rows. We scan forward from the starting position to find the first
   line-ending and then scan backwards from the last position for the last line-ending."
  [user path position chunk-size line-ending separator]
  (with-jargon (jargon-cfg) [cm]
    (log/warn "[read-csv-chunk]" user path position chunk-size line-ending separator)
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-is-file cm path)
    (validators/path-readable cm user path)

    (when-not (contains? #{"\r\n" "\n"} line-ending)
      (throw+ {:error_code "ERR_INVALID_LINE_ENDING"
               :line-ending line-ending}))

    (let [chunk         (read-at-position cm path position chunk-size)
          front-trimmed (trim-to-line-start chunk line-ending)
          new-start-pos (calc-start-pos position chunk front-trimmed)
          trimmed-chunk (trim-to-last-line front-trimmed line-ending)
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
