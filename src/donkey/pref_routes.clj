(ns donkey.pref-routes
  (:use [compojure.core]
        [donkey.user-prefs]
        [donkey.util]))

(defn secured-pref-routes
  []
  (routes
   (GET "/preferences" []
        (trap user-prefs))

   (POST "/preferences" [:as {body :body}]
         (trap #(user-prefs (slurp body))))

   (DELETE "/preferences" []
           (trap remove-prefs))

   (GET "/search-history" []
        (trap search-history))

   (POST "/search-history" [:as {body :body}]
         (trap #(search-history (slurp body))))

   (DELETE "/search-history" []
           (trap clear-search-history))))
