(ns donkey.transformers
  (:require [clojure.tools.logging :as log])
  (:import [net.sf.json JSONObject]))

(defn object->json
  "Converts a Java object to a JSON string."
  [obj]
  (.toString (JSONObject/fromObject obj)))
