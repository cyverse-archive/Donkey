(ns donkey.services.admin
  (:use [clj-jargon.init :only [with-jargon]]
        [clj-jargon.item-info :only [exists?]])
  (:require [clojure.tools.logging :as log]
            [cemerick.url :as url]
            [cheshire.core :as json]
            [clojure-commons.config :as cc]
            [clojure-commons.error-codes :as ce]
            [donkey.util.config :as config]
            [clj-http.client :as client]))
(defn config
  "Returns JSON containing Donkey's configuration, passwords filtered out."
  []
  (config/masked-config))

(defn check-irods?
  "Returns true if the iRODS settings should be checked."
  []
  (or (config/data-routes-enabled)
      (config/filesystem-routes-enabled)
      (config/fileio-routes-enabled)))

(defn check-riak?
  "Returns true if the Riak settings should be checked."
  []
  (or (config/session-routes-enabled)
      (config/filesystem-routes-enabled)
      (config/pref-routes-enabled)
      (config/tree-viewer-routes-enabled)))

(defn check-jex?
  "Returns true if the JEX settings should be checked."
  []
  (config/metadata-routes-enabled))

(defn check-metadactyl?
  "Returns true if the metadactyl settings should be checked."
  []
  (config/metadata-routes-enabled))

(defn check-notificationagent?
  "Returns true if the notification agent settings should be checked."
  []
  (config/notification-routes-enabled))

(defn scrub-url
  [url-to-scrub]
  (str (url/url url-to-scrub :path "/")))

(defn perform-irods-check
  []
  (try
    (with-jargon (config/jargon-cfg) [cm] 
      (exists? cm (:home cm)))
    (catch Exception e
      (log/error "Error performing iRODS status check:")
      (log/error (ce/format-exception e)) 
      false)))

(defn perform-riak-check
  []
  (try
    (let [s (:status (client/get (config/riak-base-url)))]
      (log/info "HTTP Status from Riak: " s)
      (<= 200 s 299))
    (catch Exception e
      (log/error "Error performing Riak status check:")
      (log/error (ce/format-exception e))
      false)))

(defn perform-jex-check
  []
  (try
    (let [s (:status (client/get (config/jex-base-url)))]
      (log/info "HTTP Status from JEX: " s)
      (<= 200 s 299))
    (catch Exception e
      (log/error "Error performing JEX status check:")
      (log/error (ce/format-exception e))
      false)))

(defn perform-metadactyl-check
  []
  (try
    (let [base-url (scrub-url (config/metadactyl-base-url))
          s        (:status (client/get base-url))]
      (log/info "HTTP Status from Metadactyl: " s)
      (<= 200 s 299))
    (catch Exception e
      (log/error "Error performing Metadactyl status check:")
      (log/error (ce/format-exception e))
      false)))

(defn perform-notificationagent-check
  []
  (try
    (let [base-url (scrub-url (config/notificationagent-base-url))
          s        (:status (client/get base-url))]
      (log/info "HTTP Status from NotificationAgent: " s)
      (<= 200 s 299))
    (catch Exception e
      (log/error "Error performing NotificationAgent status check:")
      (log/error (ce/format-exception e))
      false)))

(defn status-irods
  [status]
  (if (check-irods?)
    (merge status {:iRODS (perform-irods-check)})
    status))

(defn status-riak
  [status]
  (if (check-riak?)
    (merge status {:riak (perform-riak-check)})
    status))

(defn status-jex
  [status]
  (if (check-jex?)
    (merge status {:jex (perform-jex-check)})
    status))

(defn status-metadactyl
  [status]
  (if (check-metadactyl?)
    (merge status {:metadactyl (perform-metadactyl-check)})
    status))

(defn status-notificationagent
  [status]
  (if (check-notificationagent?)
    (merge status {:notificationagent (perform-notificationagent-check)})
    status))

(defn status
  "Returns JSON containing the Donkey's status."
  [request]
  (-> {}
    (status-irods)
    (status-riak)
    (status-jex)
    (status-metadactyl)
    (status-notificationagent)))

