(ns donkey.services.parsely
  (:require [donkey.util.service :as svc]
            [donkey.util.config :as cfg]
            [donkey.util.transformers :as xforms]))

(defn- secured-parsely-url
  [req params & components]
  (apply svc/build-url-with-query (cfg/parsely-url)
         (xforms/add-current-user-to-map params) components))

(defn triples
  "The GET /triples handler for parsely."
  [req params]
  (svc/forward-get
    (secured-parsely-url req params "triples") req))

(defn add-type
  "The POST /type handler for parsely."
  [req params]
  (svc/forward-post 
    (secured-parsely-url req params "type") req))

(defn get-types
  "The GET /type handler for parsely."
  [req params]
  (svc/forward-get
    (secured-parsely-url req params "type") req))

(defn delete-type
  "The DELETE /type handler for parsely."
  [req params]
  (svc/forward-delete
    (secured-parsely-url req params "type") req))

(defn get-type-list
  "The GET /type-list handler for parsely."
  [req params]
  (svc/forward-get
    (secured-parsely-url req params "type-list") req))

(defn find-typed-paths
  "The GET /type/paths handler for parsely."
  [req params]
  (svc/forward-get
    (secured-parsely-url req params "type" "paths") req))

