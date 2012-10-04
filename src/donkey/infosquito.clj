(ns donkey.infosquito
  "provides the functions that forward Infosquito requests"
  (:require [clojure.data.json :as dj] 
            [donkey.config :as c] 
            [donkey.service :as s]))


(defn- transform-request-body
  [orig-body user]
  (let [orig-search (dj/read-json orig-body)] 
    (dj/json-str (assoc orig-search 
                        :query {:filtered {:query  (:query orig-search)
                                           :filter {:term {:user user}}}}))))


(defn search
  "Performs a search on the Elastic Search repository.
    
   Parameters:
     request - The original request structured by compojure
     user - The user attributes for the user performing the search
     type - The mapping type used to restrict the request.

   Returns:
     the response from Elastic Search"
  [request user & [type]]
  (s/forward-get 
    (apply s/build-url (c/es-url) (remove nil? ["iplant" type "_search"]))
    request 
    (transform-request-body (slurp (:body request)) (:shortUsername user))))
