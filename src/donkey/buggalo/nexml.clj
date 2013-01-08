(ns donkey.buggalo.nexml
  (:use [clojure.java.io :only [file reader]])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [java.io PrintWriter]
           [javax.xml XMLConstants]
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

(defn format-node
  "Converts a single node in a NeXML tree into a vector representing a Newick
   string that represents the tree."
  [tree parent node]
  (let [children   (sort-by #(.getId %) (.getOutNodes tree node))
        subnodes   (mapv (partial format-node tree node) children)
        label      (.getLabel node)
        branch-len (if (nil? parent) nil (.getLength (.getEdge tree parent node)))
        full-label (if (nil? branch-len) [label] [label ":" branch-len])]
    (if-not (empty? subnodes)
      (into [] (concat ["("] (flatten (interpose [","] subnodes)) [")"] full-label))
      full-label)))

(defn- to-newick
  "Generates a newick string representing a NeXML tree."
  [tree]
  (let [count-parents #(count (seq (.getInNodes tree %)))
        root (or (.getRoot tree)
                 (first (filter #(zero? (count-parents %)) (.getNodes tree))))]
    (apply str (conj (format-node tree nil root) ";"))))

(defn- save-tree-file
  "Saves a NeXML tree to a Newick file."
  [dir index tree]
  (let [label    (.getLabel tree)
        filename (if (string/blank? label)
                   (str "tree_" index ".tre")
                   (str label ".tre"))
        out-file (file dir filename)
        newick   (to-newick tree)]
    (with-open [out (PrintWriter. out-file)]
      (.println out newick))
    out-file))

(defn extract-trees-from-nexml
  "Extracts all trees from a NeXML file."
  [dir infile]
  (let [networks (mapcat seq (.getTreeBlockList (DocumentFactory/parse infile)))
        trees    (filter (partial instance? Tree) networks)]
    (when (empty? trees)
      (throw (IllegalArgumentException. (str "no trees found in NeXML file"))))
    (mapv (partial save-tree-file dir) (range) trees)))
