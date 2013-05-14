(ns donkey.routes.tree-viewer
  (:use [compojure.core]
        [donkey.buggalo]
        [donkey.util.service]
        [donkey.user-attributes]
        [donkey.util])
  (:require [donkey.config :as config]))

(defn secured-tree-viewer-routes
  []
  (optional-routes
   [config/tree-viewer-routes-enabled]

   (GET "/tree-viewer-urls" [:as {params :params}]
        (trap #(tree-viewer-urls (required-param params :path) (:shortUsername current-user)
                                 params)))))

(defn unsecured-tree-viewer-routes
  []
  (optional-routes
   [config/tree-viewer-routes-enabled]

   (POST "/tree-viewer-urls" [:as {body :body}]
         (trap #(tree-viewer-urls-for body)))))
