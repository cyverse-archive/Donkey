(ns donkey.infosquito
  "provides the functions that forward Infosquito requests"
  (:require [clojure.data.json :as dj]
            [donkey.config :as c]
            [donkey.service :as s]))


(defn- extract-source
  [request]
  (let [source (if-let [source' (:source (:params request))]
                 source'
                 (slurp (:body request)))]
    (if (empty? source)
      (throw (Exception. "no search document provided"))
      source)))


(defn- transform-source
  [orig-source user]
  (let [orig-search (dj/read-json orig-source)]
    (dj/json-str (assoc orig-search
                        :query {:filtered {:query  (:query orig-search)
                                           :filter {:term {:user user}}}}))))


(defn- mk-url
  [base type params]
  (apply s/build-url-with-query base params (remove nil? ["iplant" type "_search"])))


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
  [request user & [type]]
  (let [source (transform-source (extract-source request) (:shortUsername user))]
    (s/forward-get (mk-url (c/es-url)
                           type
                           (assoc (dissoc (:params request) :proxyToken)
                                  :source source))
                   request)))
