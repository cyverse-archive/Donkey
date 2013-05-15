(ns donkey.services.search
  "provides the functions that forward search requests to Elastic Search"
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojurewerkz.elastisch.query :as es-query]
            [clojurewerkz.elastisch.rest :as es]
            [clojurewerkz.elastisch.rest.document :as es-doc]
            [clojurewerkz.elastisch.rest.response :as es-resp]
            [slingshot.slingshot :as ss]
            [clojure-commons.client :as client]
            [clojure-commons.nibblonian :as nibblonian]
            [donkey.config :as cfg]
            [donkey.util.service :as svc])
  (:import [java.net ConnectException]))


(defn send-request
  "Sends the search request to Elastic Search.

   Throws:  
     This throws ERR_CONFIG_INVALID when there it fails to connect to Elastic 
     Search or when it detects that Elastic Search hasn't been initialized."
  [query from size type]
  (let [index "iplant"]
    (ss/try+ 
      (es/connect! (cfg/es-url))
      (if type
        (es-doc/search index type :query query :from from :size size)
        (es-doc/search-all-types index :query query :from from :size size))
      (catch ConnectException _
        (throw (Exception. "cannot connect to Elastic Search")))
      (catch [:status 404] {:keys []}
        (throw (Exception. "Elastic Search has not been initialized"))))))


(defn- extract-result
  "Extracts the result of the Donkey search services from the results returned
   to us by Elastic Search."
  [resp]
  (letfn [(format-hit [hit] (dissoc (merge hit (:_source hit)) :_source))]
    {:total     (or (es-resp/total-hits resp) 0)
     :max_score (get-in resp [:hits :max_score])
     :hits      (map format-hit (es-resp/hits-from resp))}))


(defn- mk-query
  "Builds a query."
  [name-glob user user-groups]
  (let [pattern (string/lower-case
                 (if (re-find #"[*?]" name-glob)
                   name-glob
                   (str name-glob \*)))
        viewers (conj user-groups user)]
    (es-query/filtered :query  (es-query/wildcard :name pattern)
                       :filter (es-query/term :viewers viewers))))


(defn- extract-type
  "Extracts the entity type from the URL parameters

   Throws:
     :invalid-argument - This is thrown if the extracted type isn't valid."
  [params]
  (if-let [type-val (:type params)]
    (let [type (string/lower-case type-val)]
      (when-not (contains? #{"folder" "file"} type)
        (ss/throw+ {:error_code :invalid-argument
                    :reason "must be 'file' or 'folder'"
                    :arg    :type
                    :val    type-val}))
      type)))


(defn- extract-uint
  "Extracts a non-negative integer from the URL parameters

   Throws:
     invalid-argument - This is thrown if the parameter value isn't a
       non-negative integer."
  [params name-key default]
  (letfn [(mk-exception [val] {:type   :invalid-argument
                               :reason "must be a non-negative integer"
                               :arg    name-key
                               :val    val})]
    (if-let [val-str (name-key params)]
      (ss/try+
        (let [val (Integer. val-str)]
          (when (neg? val)
            (ss/throw+ (mk-exception val)))
          val)
        (catch NumberFormatException _
          (ss/throw+ (mk-exception val-str))))
      default)))


(defn search
  "Performs a search on the Elastic Search repository.  The value of the
   search-term query-string parameter is used as the name pattern to search for.
   If search-term contains an asterisk or a question mark then it will be
   treated as a literal glob pattern.  Otherwise, an asterisk will be added to
   the end of the search term and that value will be used as a glob pattern.

   Optionally, the type, from and size parameters may be provided in the query
   string.  The type parameter is the type of entry to search, either file or
   folder.  If type isn't provided, all entries will be searched.  The from and
   size parameters are used for paging.  from indicates the number of entries to
   skip before returns matches.  size indicates the number of matches to return.
   If from isn't provided, it defaults to 0.  If size isn't provided, it
   defaults to 10.

   Parameters:
     params     - the query-string parameters for this service.
     user-attrs - the attributes of the user performing the search.

   Returns:
     the response from Elastic Search"
  [params {user :shortUsername}]
  (when-not user
    (throw (Exception. "no user provided for search")))
  (let [search-term (svc/required-param params :search-term)
        type        (extract-type params)
        from        (extract-uint params :from 0)
        size        (extract-uint params :size 10)
        groups      (nibblonian/get-user-groups (cfg/nibblonian-base-url) user)]
    (-> (mk-query search-term user groups)
      (send-request from size type)
      extract-result
      svc/success-response)))
