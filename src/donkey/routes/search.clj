(ns donkey.routes.search
  "the routing code for search-related URL resources"
  (:use [compojure.core :only [GET]])
  (:require [donkey.auth.user-attributes :as user]
            [donkey.services.search :as search]
            [donkey.util :as util]
            [donkey.util.config :as config]))


(defn secured-search-routes
  "The routes for search-related endpoints."
  []
  (util/optional-routes
    [config/search-routes-enabled]

    (GET "/filesystem/index" [q & opts]
      (search/search (search/qualify-user (:shortUsername user/current-user)) q opts))))
