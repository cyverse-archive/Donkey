(ns donkey.infosquito
  "provides the functions that forward Infosquito requests"
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojurewerkz.elastisch.query :as es-query]
            [clojurewerkz.elastisch.rest :as es]
            [clojurewerkz.elastisch.rest.document :as es-doc]
            [clojurewerkz.elastisch.rest.response :as es-resp]
            [donkey.config :as cfg]
            [donkey.service :as svc]))


(defn- send-request
  "Sends the search request to Elastic Search."
  [query from size type]
  (let [index "iplant"]
    (es/connect! (cfg/es-url))
    (if type
      (es-doc/search index type :query query :from from :size size)
      (es-doc/search-all-types index :query query :from from :size size))))
    

(defn- extract-result
  "Extracts the result of the Donkey search services from the results returned
   to us by Elastic Search."
  [resp]
  (letfn [(format-hit [hit] (dissoc (merge hit (:_source hit)) :_source))]
    {:total     (or (es-resp/total-hits resp) 0)
     :max_score (get-in resp [:hits :max_score])
     :hits      (map format-hit (es-resp/hits-from resp))})) 


(defn- mk-query
  "Builds a query to use for a simple search."
  [name-glob user]
  (let [query (es-query/wildcard :name (if (re-find #"[*?]" name-glob)
                                         name-glob
                                         (str name-glob \*)))]
    (es-query/filtered :query query :filter (es-query/term :user user))))

      
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
  [{:keys [search-term type from size]} {user :shortUsername}]
  (when-not user
    (throw (IllegalArgumentException. "no user provided for search")))  
  (let [type' (and type (string/lower-case type))
        from' (if from (Integer. from) 0)
        size' (if size (Integer. size) 10)]
    (-> (mk-query search-term user)
      (send-request from' size' type')
      extract-result
      svc/success-response)))
