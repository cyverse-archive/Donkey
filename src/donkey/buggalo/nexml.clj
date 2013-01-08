(ns donkey.buggalo.nexml
  (:use [clojure.java.io :only [file reader]])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [javax.xml XMLConstants]
           [javax.xml.transform.stream StreamSource]
           [javax.xml.validation SchemaFactory]
           [org.nexml.model DocumentFactory Tree]
           [org.xml.sax ErrorHandler]))

(defn- get-resource
  "Loads a resource from the classpath."
  [resource-name]
  (-> (Thread/currentThread)
      (.getContextClassLoader)
      (.getResource resource-name)))

(defn- xsd-error-handler
  "Creates an error handler that can be used during the validation of an XML document."
  [is-valid?]
  (reify ErrorHandler
    (error [this e]
      (log/debug "XML schema validation error:" e)
      (reset! is-valid? false))
    (fatalError [this e]
      (log/debug "fatal XML schema validation error:" e)
      (reset! is-valid? false))
    (warning [this e]
      (log/debug "XML schema validation warning:" e))))

(defn- load-schema
  "Loads an XML schema from a file somewhere on the classpath."
  [path]
  (.newSchema (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
              (StreamSource. (file (get-resource path)))))

(defn- first-line
  "Loads the firt non-blank line from a file."
  [f]
  (with-open [rdr (reader f)]
    (first (remove string/blank? (line-seq rdr)))))

(defn is-nexml?
  "Determines if a file is a NeXML file."
  [infile]
  (when (re-find #"^<" (first-line infile))
    (let [schema    (load-schema "nexml/xsd/nexml.xsd")
          is-valid? (atom true)
          validator (.newValidator schema)]
      (.setErrorHandler validator (xsd-error-handler is-valid?))
      (.validate validator (StreamSource. infile))
      @is-valid?)))

(defn- to-newick
  [node]
  (log/warn node))

(defn- extract-tree
  [dir index tree]
  (to-newick
   (or (.getRoot tree)
       (first (filter #(zero? (count (seq (.getInNodes tree %)))) (.getNodes tree))))))

(defn extract-trees-from-nexml
  [dir infile]
  (let [networks (mapcat seq (.getTreeBlockList (DocumentFactory/parse infile)))
        trees    (filter (partial instance? Tree) networks)]
    (dorun (map (partial extract-tree dir) (range) trees)))
  (throw (IllegalArgumentException. "NeXML is not currently supported")))
