(ns donkey.tree-viewer-routes
  (:use [compojure.core]
        [donkey.buggalo]
        [donkey.service]
        [donkey.user-attributes]
        [donkey.util]))

(defn secured-tree-viewer-routes
  []
  (routes
   (GET "/tree-viewer-urls" [:as {params :params}]
        (trap #(tree-viewer-urls (required-param params :path) (:shortUsername current-user)
                                 params)))))

(defn unsecured-tree-viewer-routes
  []
  (routes
   (POST "/tree-viewer-urls" [:as {body :body}]
         (trap #(tree-viewer-urls-for body)))))
