(ns donkey.util
  "Utility functions for defining services in Donkey. This namespace is used by donkey.core and
   several other top-level service definition namespaces."
  (:use [donkey.service]
        [slingshot.slingshot :only [try+]])
  (:require [clojure-commons.error-codes :as ce]))

(defn trap
  "Traps any exception thrown by a service and returns an appropriate
   repsonse."
  [f]
  (try+
   (f)
   (catch [:type :error-status] {:keys [res]} res)
   (catch [:type :missing-argument] {:keys [arg]} (missing-arg-response arg))
   (catch [:type :invalid-argument] {:keys [arg val reason]}
     (invalid-arg-response arg val reason))
   (catch [:type :temp-dir-failure] err (temp-dir-failure-response err))
   (catch [:type :tree-file-parse-err] err (tree-file-parse-err-response err))
   (catch ce/error? err (common-error-code &throw-context))
   (catch IllegalArgumentException e (failure-response e))
   (catch IllegalStateException e (failure-response e))
   (catch Throwable t (error-response t))))

(defn as-vector
  "Returns the given parameter inside a vector if it's not a vector already."
  [p]
  (cond (nil? p)    []
        (vector? p) p
        :else       [p]))
