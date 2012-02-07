(ns donkey.metadactyl
  (:import [org.hibernate.cfg Configuration]
           [org.iplantc.workflow.service WorkflowElementRetrievalService]))

(def
  ^{:private true
    :doc "The list of initialization functions to call."}
   beans (ref []))

(defmacro defbean
  "Defines a bean that needs to be initialized when the system is started."
  [sym docstr & init-forms]
  `(def ~(with-meta sym {:doc docstr}) (memoize (fn [] ~@init-forms))))

(defn register-bean
  "Adds a bean initialization functions to the list."
  [new-bean]
  (dosync (alter beans conj new-bean)))

(register-bean (defbean foo "The Foo" (prn "Initializing the Foo!") "Bar"))

(defn init
  "Initializes the beans to use for all metadactyl services."
  []
  (dorun (map #(%) @beans)))
