(ns donkey.infosquito
  "provides the functions that forward Infosquito requests"
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure-commons.client :as client]
            [donkey.config :as cfg]
            [donkey.service :as svc]))


(defn send-request
  "Sends the search request to Elastic Search."
  [query]
  (let [url (svc/build-url (cfg/es-url) "iplant" "_search")]
    (client/get url {:query-params {"source" (json/json-str query)}})))


(defn- extract-source
  [request]
  (let [source (if-let [source' (get-in request [:params :source])]
                 source'
                 (slurp (:body request)))]
    (if (empty? source)
      (throw (IllegalArgumentException. "no search document provided"))
      source)))


(defn- build-filter
  [m]
  (when (nil? (:user m))
    (throw (IllegalArgumentException. "no user provided for search")))
  (let [terms (->> (remove (comp nil? val) m)
                   (map (fn [[k v]] {:term {k v}})))]
    (if (= 1 (count terms))
      (first terms)
      {:and terms})))

(defn- transform-source
  [orig-source user type]
  (let [orig-search (json/read-json orig-source)
        filt        (build-filter {:user user :_type type})]
    (assoc orig-search
      :query {:filtered {:query  (:query orig-search)
                         :filter filt}})))


(defn- mk-url
  [base type params]
  (apply svc/build-url-with-query base params (remove nil? ["iplant" type "_search"])))


(defn- extract-result
  "Extracts the result of the Donkey search services from the results returned
   to us by Elastic Search."
  [body]
  (letfn [(flatten-source [m] (dissoc (merge m (:_source m)) :_source))
          (flatten-sources [s] (map flatten-source s))
          (reformat-result [m] (update-in m [:hits] flatten-sources))]
   (->> (json/read-json body)
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
      (transform-source user (and type (string/lower-case type)))
      (send-request)
      :body
      extract-result
      svc/success-response))


(defn- simple-query
  "Builds a query to use for a simple search."
  [search-term user type params]
  (let [params (or params {})
        query  (if (re-find #"[*?]" search-term)
                 {:wildcard {:name search-term}}
                 {:wildcard {:name (str \* search-term \*)}})
        filt   (build-filter {:user user :_type type})]
    (assoc params
      :query {:filtered {:query  query
                         :filter filt}})))


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
  (let [type (or type (:type params))
        type (and type (string/lower-case type))]
    (-> (simple-query search-term user type (dissoc params :search-term :proxytoken :type))
        (send-request)
        :body
        extract-result
        svc/success-response)))
