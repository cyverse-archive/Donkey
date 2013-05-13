(ns donkey.collaborator-routes
  (:use [compojure.core]
        [donkey.metadactyl]
        [donkey.util])
  (:require [donkey.config :as config]))

(defn secured-collaborator-routes
  []
  (optional-routes
   [config/collaborator-routes-enabled]

   (GET "/collaborators" [:as req]
        (trap #(get-collaborators req)))

   (POST "/collaborators" [:as req]
         (trap #(add-collaborators req)))

   (POST "/remove-collaborators" [:as req]
         (trap #(remove-collaborators req)))))
