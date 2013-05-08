(ns donkey.data-routes
  (:use [compojure.core]
        [donkey.sharing :only [share unshare]]
        [donkey.util])
  (:require [donkey.parsely :as parsely]))

(defn secured-data-routes
  "The routes for data sharing endpoints."
  []
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
        (trap #(unshare req))))
