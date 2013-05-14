(ns donkey.routes.session
  (:use [compojure.core]
        [donkey.user-sessions]
        [donkey.util])
  (:require [donkey.config :as config]))

(defn secured-session-routes
  []
  (optional-routes
   [config/session-routes-enabled]

   (GET "/sessions" []
        (trap user-session))

   (POST "/sessions" [:as {body :body}]
         (trap #(user-session (slurp body))))

   (DELETE "/sessions" []
           (trap remove-session))))
