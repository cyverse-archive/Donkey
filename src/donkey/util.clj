(ns donkey.util
  "Utility functions for defining services in Donkey. This namespace is used by donkey.core and
   several other top-level service definition namespaces."
  (:use [compojure.core]
        [donkey.util.service]
        [slingshot.slingshot :only [try+]])
  (:require [clojure-commons.error-codes :as ce]
            [clojure.tools.logging :as log]))

(defn determine-response
  [resp-val]
  (if (and (map? resp-val) (number? (:status resp-val)))
    (donkey-response resp-val (:status resp-val))
    (success-response resp-val)))

(defn clj-http-error?
  [{:keys [status body]}]
  (and (number? status) ((comp not nil?) body)))

(defn trap
  "Traps any exception thrown by a service and returns an appropriate
   repsonse."
  [f]
  (try+
   (determine-response (f))
   (catch [:type :error-status] {:keys [res]} res)
   (catch [:type :missing-argument] {:keys [arg]} (missing-arg-response arg))
   (catch [:type :invalid-argument] {:keys [arg val reason]}
     (invalid-arg-response arg val reason))
   (catch [:type :temp-dir-failure] err (temp-dir-failure-response err))
   (catch [:type :tree-file-parse-err] err (tree-file-parse-err-response err))

   (catch ce/error? err
     (log/error (ce/format-exception (:throwable &throw-context)))
     (error-response err))

   (catch IllegalArgumentException e (failure-response e))
   (catch IllegalStateException e (failure-response e))
   (catch Throwable t (error-response t))
   (catch clj-http-error? o o)
   (catch Object o (error-response (Exception. (str "unexpected error: " o))))))

(defn trap-handler
  [handler]
  (fn [req]
    (trap #(handler req))))

(defn req-logger
  [handler]
  (fn [req]
    (log/info "Request received:" req)
    (handler req)))

(defn as-vector
  "Returns the given parameter inside a vector if it's not a vector already."
  [p]
  (cond (nil? p)    []
        (vector? p) p
        :else       [p]))

(defn optional-routes
  "Creates a set of optionally defined routes."
  [[option-fn] & handlers]
  (when (option-fn)
    (apply routes handlers)))

(defn flagged-routes
  "Creates a set of routes, removing any nil route definitions."
  [& handlers]
  (apply routes (remove nil? handlers)))
