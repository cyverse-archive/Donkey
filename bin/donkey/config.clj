(ns donkey.config)

(def
  ^{:doc "The name of the properties file."}
  prop-file "donkey.properties")

(def props
  ^{:doc "The properites that have been loaded from Zookeeper."}
  (atom nil))

(defn listen-port
  "The port that Donkey listens to."
  []
  (get @props "donkey.app.listen-port"))
