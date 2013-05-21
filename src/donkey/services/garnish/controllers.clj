(ns donkey.services.garnish.controllers
  (:use [slingshot.slingshot :only [try+ throw+]]
        [clojure-commons.error-codes]
        [donkey.util.transformers :only [add-current-user-to-map]])
  (:require [cheshire.core :as json]
            [hoot.rdf :as rdf]
            [hoot.csv :as csv]
            [clojure.core.memoize :as memo]
            [clojure.tools.logging :as log]
            [donkey.services.garnish.irods :as prods]))

(def script-types 
  ["ace"
   "blast"
   "bowtie"
   "clustalw"
   "codata"
   "csv"
   "embl"
   "fasta"
   "fastq"
   "fastxy"
   "game"
   "gcg"
   "gcgblast"
   "gcgfasta"
   "gde"
   "genbank"
   "genscan"
   "gff"
   "hmmer"
   "nexus"
   "mase"
   "mega"
   "msf"
   "phrap"
   "pir"
   "pfam"
   "phylip"
   "prodom"
   "raw"
   "rsf"
   "selex"
   "stockholm"
   "swiss"
   "tab"
   "vcf"])

(defn check-missing-params
  [params required-keys]
  (let [not-valid? #(not (contains? params %))]
    (if (some not-valid? required-keys)
    (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
             :fields (filter not-valid? required-keys)}))))

(defn parse-body
  [body]
  (try+
    (json/parse-string body true)
    (catch Exception e
      (throw+ {:error_code ERR_INVALID_JSON
               :message (str e)}))))

(defn check-params-valid
  [params func-map]
  (let [not-valid? #(not ((last %1) (get params (first %1))))
        field-seq  (seq func-map)]
    (when (some not-valid? field-seq)
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :fields     (mapv first (filter not-valid? field-seq))}))))

(defn validate-params
  [params func-map]
  (check-missing-params params (keys func-map))
  (check-params-valid params func-map))

(defn accepted-types
  []
  (set (concat rdf/accepted-languages csv/csv-types)))

(defn add-type
  [req-body req-params]
  (let [body   (parse-body (slurp req-body))
        params (add-current-user-to-map req-params)]
    (validate-params params {:user string?})
    (validate-params body {:path string? :type #(contains? (accepted-types) %)})
    (json/generate-string
      (prods/add-type (:user params) (:path body) (:type body)))))

(defn delete-type
  [req-params]
  (let [params (add-current-user-to-map req-params)] 
    (validate-params params {:user string? :type #(contains? (accepted-types) %) :path string?})
    (json/generate-string
      (prods/delete-type (:user params) (:path params) (:type params)))))

(defn get-types
  [req-params]
  (let [params (add-current-user-to-map req-params)] 
    (validate-params params {:path string? :user string?})
    (json/generate-string
      {:types (prods/get-types (:user params) (:path params))})))

(defn find-typed-paths
  [req-params]
  (let [params (add-current-user-to-map req-params)] 
    (validate-params params {:user string? :type string?})
    (json/generate-string
      {:paths (prods/find-paths-with-type (:user params) (:path params))})))

(defn get-type-list 
  [] 
  (json/generate-string {:types (seq (set (concat csv/csv-types script-types)))}))

(defn set-auto-type
  [req-body req-params]
  (let [body   (parse-body (slurp req-body))
        params (add-current-user-to-map req-params)]
    (log/warn body)
    (validate-params params {:user string?})
    (validate-params body {:path string?})
    (json/generate-string
      (prods/auto-add-type (:user params) (:path body)))))

(defn preview-auto-type
  [req-params]
  (let [params (add-current-user-to-map req-params)] 
    (validate-params params {:user string? :path string?})
    (json/generate-string
      (prods/preview-auto-type (:user params) (:path params)))))
