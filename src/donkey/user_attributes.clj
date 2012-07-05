(ns donkey.user-attributes
  (:use [clj-cas.cas-proxy-auth :only (validate-cas-proxy-ticket)]
        [donkey.config])
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
