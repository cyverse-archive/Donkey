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

