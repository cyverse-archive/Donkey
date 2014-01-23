(ns donkey.services.search
  "provides the functions that forward search requests to Elastic Search"
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.string :as string]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [clojurewerkz.elastisch.query :as es-query]
            [clojurewerkz.elastisch.rest :as es]
            [clojurewerkz.elastisch.rest.document :as es-doc]
            [clojurewerkz.elastisch.rest.response :as es-resp]
            [donkey.services.filesystem.users :as users]
            [donkey.util.config :as cfg]
            [donkey.util.service :as svc])
  (:import [java.net ConnectException]))


(def ^{:private true :const true} default-zone "iplant")


; TODO move this to a namespace in the client package. Also consider creating a protocol for this so that it may be mocked.
(defn- send-request
  "Sends the search request to Elastic Search.

   Throws:
     :invalid-configuration - This is thrown if there is a problem with elasticsearch
     :invalid-query - This is thrown if the query string is invalid."
  [query from size type]
  (let [index    "data"
        fmt-type (fn [t] (case t
                           :file   "file"
                           :folder "folder"))]
    (try+
      (es/connect! (cfg/es-url))
      (if (= type :any)
        (es-doc/search-all-types index :query query :from from :size size)
        (es-doc/search index (fmt-type type) :query query :from from :size size))
      (catch ConnectException _
        (throw+ {:type   :invalid-configuration
                 :reason "cannot connect to elasticsearch"}))
      (catch [:status 404] {:keys []}
        (throw+ {:type   :invalid-configuration
                 :reason "elasticsearch has not been initialized"}))
      (catch [:status 400] {:keys []}
        (throw+ {:type :invalid-query})))))


(defn- extract-result
  "Extracts the result of the Donkey search services from the results returned to us by
   ElasticSearch."
  [resp offset]
  (letfn [(format-match [match] {:score  (:_score match)
                                 :type   (:_type match)
                                 :entity (:_source match)})]
    {:total   (or (es-resp/total-hits resp) 0)
     :offset  offset
     :matches (map format-match (es-resp/hits-from resp))}))


(defn- mk-query
  "Builds a query."
  [query user user-groups]
  (let [memberships (conj user-groups user)
        filter      (es-query/nested :path   "userPermissions"
                                     :filter (es-query/term "userPermissions.user" memberships))]
    (es-query/filtered :query query :filter filter)))


(defn- extract-type
  "Extracts the entity type from the URL parameters

   Throws:
     :invalid-argument - This is thrown if the extracted type isn't valid."
  [params default]
  (if-let [type-val (:type params)]
    (case (string/lower-case type-val)
      "any"    :any
      "file"   :file
      "folder" :folder
               (throw+ {:type   :invalid-argument
                        :reason "must be 'any', 'file' or 'folder'"
                        :arg    :type
                        :val    type-val}))
    default))


(defn- extract-uint
  "Extracts a non-negative integer from the URL parameters

   Throws:
     :invalid-argument - This is thrown if the parameter value isn't a non-negative integer."
  [params name-key default]
  (letfn [(mk-exception [val] {:type   :invalid-argument
                               :reason "must be a non-negative integer"
                               :arg    name-key
                               :val    val})]
    (if-let [val-str (name-key params)]
      (try+
        (let [val (Integer. val-str)]
          (when (neg? val)
            (throw+ (mk-exception val)))
          val)
        (catch NumberFormatException _
          (throw+ (mk-exception val-str))))
      default)))


(defn qualify-name
  "Qualifies a user or group name with the default zone."
  [name]
  (str name \# default-zone))


(defn add-timing
  [result start]
  (let [curr-time  (l/local-now)
        t-interval (t/in-millis (t/interval start curr-time))]
    (assoc result :execution-time t-interval)))


; TODO make this work for users that belong to zones other than the default one.
(defn- list-user-groups
  "Looks up the groups a user belongs to. The result is a set of zone-qualified group names.
   Unqualified user names are assumed to belong to the default zone."
  [user]
  (map qualify-name
       (users/list-user-groups (string/replace user (str \# default-zone) ""))))


(defn search
  "Performs a search on the Elastic Search repository."
  [user query opts]
  (try+
    (let [type   (extract-type opts :any)
          start  (l/local-now)
          offset (extract-uint opts :offset 0)
          limit  (extract-uint opts :limit (cfg/default-search-result-limit))]
      (-> (mk-query query user (list-user-groups user))
        (send-request offset limit type)
        (extract-result offset)
        (add-timing start)
        svc/success-response))
    (catch [:type :invalid-argument] {:keys [arg val reason]}
      (svc/invalid-arg-response arg val reason))
    (catch [:type :invalid-query] {:keys []}
      (svc/invalid-arg-response "q" query "This is not a valid elasticsearch query."))))
