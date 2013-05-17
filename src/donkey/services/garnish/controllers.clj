(ns donkey.services.garnish.controllers
  (:use [slingshot.slingshot :only [try+ throw+]]
        [clojure-commons.error-codes])
  (:require [cheshire.core :as json]
            [hoot.rdf :as rdf]
            [hoot.csv :as csv]
            [clojure.core.memoize :as memo]
            [donkey.services.garnish.irods :as prods]))

(defn check-missing-params
  [params required-keys]
  (let [not-valid? #(not (contains? params %))]
    (if (some not-valid? required-keys)
    (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
             :fields (filter not-valid? required-keys)}))))

(defn parse-body
  [body]
  (try+
    (json/parse-string body :true)
    (catch Exception e
      (throw+ {:error_code ERR_INVALID_JSON
               :message e}))))

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
  [req-body params]
  (let [body (parse-body req-body)]
    (validate-params params {:user string?})
    (validate-params body {:path string? :type #(contains? (accepted-types) %)})
    (json/generate-string
      (prods/add-type (:user params) (:path body) (:type body)))))

(defn delete-type
  [params]
  (validate-params params {:user string? :type #(contains? (accepted-types) %) :path string?})
  (json/generate-string
    (prods/delete-type (:user params) (:path params) (:type params))))

(defn get-types
  [params]
  (validate-params params {:path string? :user string?})
  (json/generate-string
    {:types (prods/get-types (:user params) (:path params))}))

(defn find-typed-paths
  [params]
  (validate-params params {:user string? :type string?})
  (json/generate-string
    {:paths (prods/find-paths-with-type (:user params) (:path params))}))

(defn get-type-list 
  [] 
  (json/generate-string {:types csv/csv-types}))

(defn set-auto-type
  [req-body params]
  (let [body (parse-body req-body)]
    (validate-params params {:user string?})
    (validate-params body {:path string?})
    (json/generate-string
      (prods/auto-add-type (:user params) (:path body)))))

(defn preview-auto-type
  [params]
  (validate-params params {:user string? :path string?})
  (json/generate-string
    (prods/preview-auto-type (:user params) (:path params))))
