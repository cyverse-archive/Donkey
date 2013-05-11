(ns donkey.session-routes
  (:use [compojure.core]
        [donkey.user-sessions]
        [donkey.util]))

(defn secured-session-routes
  []
  (routes
   (GET "/sessions" []
        (trap user-session))

   (POST "/sessions" [:as {body :body}]
         (trap #(user-session (slurp body))))

   (DELETE "/sessions" []
           (trap remove-session))))
