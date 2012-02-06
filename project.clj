(defproject Donkey "1.0.0-SNAPSHOT"
  :description "Framework for hosting DiscoveryEnvironment metadata services."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [compojure "1.0.1"]
                 [swank-clojure "1.4.0-SNAPSHOT"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [ring/ring-jetty-adapter "1.0.1"]]
  :dev-dependencies [[lein-ring "0.4.5"]
                     [swank-clojure "1.2.1"]]
  :extra-classpath-dirs ["conf"]
  :aot [donkey.core]
  :main donkey.core
  :ring {:handler donkey.core/app}
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})
