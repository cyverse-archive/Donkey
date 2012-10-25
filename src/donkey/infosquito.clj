(ns donkey.infosquito
  "provides the functions that forward Infosquito requests"
  (:require [clojure.data.json :as dj]
            [clojure-commons.client :as client]
            [donkey.config :as c]
            [donkey.service :as s]))


(defn send-request
  "Sends the search request to Elastic Search."
  [query & [type]]
  (let [components (remove nil? ["iplant" type "_search"])
        url        (apply s/build-url (c/es-url) components)]
    (client/get url {:query-params {"source" (dj/json-str query)}})))


(defn- extract-source
  [request]
  (let [source (if-let [source' (get-in request [:params :source])]
                 source'
                 (slurp (:body request)))]
    (if (empty? source)
      (throw (Exception. "no search document provided"))
      source)))


(defn- transform-source
  [orig-source user]
  (let [orig-search (dj/read-json orig-source)]
    (assoc orig-search
      :query {:filtered {:query  (:query orig-search)
                         :filter {:term {:user user}}}})))


(defn- mk-url
  [base type params]
  (apply s/build-url-with-query base params (remove nil? ["iplant" type "_search"])))


(defn- extract-result
  "Extracts the result of the Donkey search services from the results returned
   to us by Elastic Search."
  [body]
  (letfn [(flatten-source [m] (dissoc (merge m (:_source m)) :_source))
          (flatten-sources [s] (map flatten-source s))
          (reformat-result [m] (update-in m [:hits] flatten-sources))]
   (->> (dj/read-json body)
        :hits
        reformat-result)))


(defn search
  "Performs a search on the Elastic Search repository.  The filtered search JSON
   document will be passed to Elastic Search as the source parameter in the
   query string.  The orginal search document may come from either the source
   parameter in the query string or from the request body.  If both are provided,
   the one provided in the query string will be used.

   Parameters:
     request - The original request structured by compojure
     user - The user attributes for the user performing the search
     type - The mapping type used to restrict the request.

   Returns:
     the response from Elastic Search"
  [request {user :shortUsername} & [type]]
  (-> (extract-source request)
      (transform-source user)
      (send-request type)
      :body
      extract-result
      s/success-response))


(defn- simple-query
  "Builds a query to use for a simple search."
  [search-term user params]
  (let [params (or params {})
        query  (if (re-find #"[*?]" search-term)
                 {:wildcard {:name search-term}}
                 {:wildcard {:name (str \* search-term \*)}})]
    (assoc params
      :query {:filtered {:query  query
                         :filter {:term {:user user}}}})))


(defn simple-search
  "Performs a simple search on the Elastic Search repository.  The value of the
   search-term query-string parameter is used as the name pattern to search for.
   If the search term contains an asterisk or a question mark then it will be
   treated as a literal wildcard glob pattern.  Otherwise, asterisks will be
   added to the beginning and end of the search term and that value will be used
   as a glob pattern.

   Parameters:
     params     - the query-string parameters for this service.
     user-attrs - the attributes of the user performing the search.
     type       - the mapping type used to restrict the request.

   Returns:
     the response from Elastic Search"
  [{:keys [search-term] :as params} {user :shortUsername} & [type]]
  (-> (simple-query search-term user (dissoc params :search-term :proxyToken))
      (send-request type)
      :body
      extract-result
      s/success-response))
