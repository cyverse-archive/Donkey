(ns donkey.routes.search
  "the routing code for search-related URL resources"
  (:use [compojure.core :only [GET]])
  (:require [donkey.util :as util]
            [donkey.util.config :as config]))


(defn unsecured-search-routes
  "The routes for search-related endpoints."
  []
  (util/optional-routes
    [config/search-routes-enabled]

    (GET "/filesystem/index" [:as req]
     "/filesystem/index reached")

    (GET "/filesystem/index/*" [:as req]
      "/filesystem/index/* reached")

    (GET "filesystem/index-status" [:as req]
      "/filesystem/index-status reached")))
