(ns donkey.data-routes
  (:use [compojure.core]
        [donkey.file-listing]
        [donkey.sharing :only [share unshare]]
        [donkey.user-attributes]
        [donkey.util])
  (:require [donkey.config :as config]
            [donkey.parsely :as parsely]
            [donkey.search :as search]))

(defn secured-data-routes
  "The routes for data sharing endpoints."
  []
  (optional-routes
   [config/data-routes-enabled]

   (GET "/parsely/triples" [:as req]
        (trap #(parsely/triples req (:params req))))

   (GET "/parsely/type" [:as req]
        (trap #(parsely/get-types req (:params req))))

   (POST "/parsely/type" [:as req]
         (trap #(parsely/add-type req (:params req))))

   (GET "/parsely/type/paths" [:as req]
        (trap #(parsely/find-typed-paths req (:params req))))

   (GET "/triples" [:as req]
        (trap #(parsely/triples req (:params req))))

   (POST "/share" [:as req]
         (trap #(share req)))

   (POST "/unshare" [:as req]
         (trap #(unshare req)))

   (GET "/search" [:as {params :params}]
        (trap #(search/search params current-user)))))
