(ns donkey.user-info-routes
  (:use [compojure.core]
        [donkey.user-info]
        [donkey.util])
  (:require [donkey.config :as config]))

(defn secured-user-info-routes
  []
  (optional-routes
   [config/user-info-routes-enabled]

   (GET "/user-search/:search-string" [search-string :as req]
        (trap #(user-search search-string (get-in req [:headers "range"]))))

   (GET "/user-info" [:as {params :params}]
        (trap #(user-info (as-vector (:username params)))))))
