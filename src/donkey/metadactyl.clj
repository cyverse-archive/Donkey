(ns donkey.metadactyl
  (:import [org.hibernate.cfg Configuration]
           [org.iplantc.workflow.service WorkflowElementRetrievalService]))

(def
  ^{:private true
    :doc "The list of initialization functions to call."}
   init-functions (ref []))

(defmacro defbean
  "Defines a bean that needs to be initialized when the system is started."
  [sym docstr & init-forms]
  (let [init-sym (gensym "init-")]
    `(let [~init-sym (memoize (fn [] ~@init-forms))]
       (def ~(with-meta sym {:doc docstr}) ~init-sym))))

(defbean foo
  "The Foo"
  (prn "Initializing the Foo!")
  "Bar")

(defn init
  "Initializes the beans to use for all metadactyl services."
  []
  (dorun (map #(%) @init-functions)))
