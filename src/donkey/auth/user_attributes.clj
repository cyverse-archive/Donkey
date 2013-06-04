(ns donkey.auth.user-attributes
  (:use [clj-cas.cas-proxy-auth :only (validate-cas-proxy-ticket)]
        [donkey.util.config])
  (:require [clojure.tools.logging :as log]))

(def
  ^{:doc "The authenticated user or nil if the service is unsecured."
    :dynamic true}
   current-user nil)

(defn- user-from-attributes
  "Creates a map of values from user attributes stored in the request by
   validate-cas-proxy-ticket."
  [{:keys [user-attributes]}]
  (log/trace user-attributes)
  {:username (str (get user-attributes "uid") "@" (uid-domain)),
   :password (get user-attributes "password"),
   :email (get user-attributes "email"),
   :shortUsername (get user-attributes "uid")})

(defn fake-user-from-attributes
  "Creates a real map of fake values for a user base on environment variables."
  [placeholder & args]
  {:username (System/getenv "IPLANT_CAS_USER")
   :password (System/getenv "IPLANT_CAS_PASS")
   :email    (System/getenv "IPLANT_CAS_EMAIL")
   :shortUsername (System/getenv "IPLANT_CAS_SHORT")})

(defn store-current-user
  "Authenticates the user using validate-cas-proxy-ticket and binds
   current-user to a map that is built from the user attributes that
   validate-cas-proxy-ticket stores in the request."
  [handler cas-server-fn server-name-fn]
  (validate-cas-proxy-ticket
    (fn [request]
      (binding [current-user (user-from-attributes request)]
        (handler request)))
    cas-server-fn server-name-fn))

(defn fake-store-current-user
  "Fake storage of a user"
  [handler cas-server-fn server-name-fn]
  (fn [req]
    (binding [current-user (fake-user-from-attributes req)]
      (handler req))))