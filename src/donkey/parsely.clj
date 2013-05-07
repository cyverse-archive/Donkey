(ns donkey.parsely
  (:require [donkey.service :as svc]
            [donkey.config :as cfg]
            [donkey.transformers :as xforms]))

(defn- secured-parsely-url
  [req params & components]
  (apply svc/build-url-with-query (cfg/parsely-url)
         (xforms/add-current-user-to-map params) components))

(defn triples
  [req params]
  (svc/forward-get
    (secured-parsely-url req params "triples") req))

(defn add-type
  [req params]
  (svc/forward-post 
    (secured-parsely-url req params "type") req))

(defn get-types
  [req params]
  (svc/forward-get
    (secured-parsely-url req params "type") req))

(defn find-typed-paths
  [req params]
  (svc/forward-get
    (secured-parsely-url req params "type" "paths") req))

