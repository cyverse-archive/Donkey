(ns donkey.services.admin
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clojure-commons.config :as cc]
            [donkey.util.config :as config]))
(defn config
  "Returns JSON containing Donkey's configuration, passwords filtered out."
  []
  (config/masked-config))

(defn status
  "Returns JSON containing the Donkey's status."
  [request]
  {"iRODS" "f00"})

