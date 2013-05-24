(ns donkey.clients.metadactyl
  (:use [donkey.auth.user-attributes :only [current-user]])
  (:require [cemerick.url :as curl]
            [clj-http.client :as client]
            [donkey.util.config :as config]
            [clojure.tools.logging :as log]))

(defn- secured-params
  ([]
     (secured-params {}))
  ([existing-params]
     (assoc existing-params
       :user  (:shortUsername current-user)
       :email (:email current-user))))

(defn- unsecured-url
  [& components]
  (str (apply curl/url (config/metadactyl-unprotected-base-url) components)))

(defn- secured-url
  [& components]
  (str (apply curl/url (config/metadactyl-base-url) components)))

(defn get-only-app-groups
  []
  (client/get (secured-url "app-groups")
              {:query-params (secured-params)}))
