(ns donkey.collaborator-routes
  (:use [compojure.core]
        [donkey.metadactyl]
        [donkey.util]))

(def secured-collaborator-routes
  []
  (routes
   (GET "/collaborators" [:as req]
        (trap #(get-collaborators req)))

   (POST "/collaborators" [:as req]
         (trap #(add-collaborators req)))

   (POST "/remove-collaborators" [:as req]
         (trap #(remove-collaborators req)))))
