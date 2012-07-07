(defproject donkey "1.3.0-SNAPSHOT"
  :description "Framework for hosting DiscoveryEnvironment metadata services."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/data.json "0.1.2"]
                 [clj-http "0.4.3"]
                 [com.cemerick/url "0.0.6"]
                 [compojure "1.0.1"]
                 [org.iplantc/clj-cas "1.0.0-SNAPSHOT"]
                 [org.iplantc/clojure-commons "1.2.0-SNAPSHOT"]
                 [ring/ring-jetty-adapter "1.0.1"]
                 [net.sf.json-lib/json-lib "2.4" :classifier "jdk15"]
                 [slingshot "0.10.1"]]
  :plugins [[org.iplantc/lein-iplant-rpm "1.3.0-SNAPSHOT"]
            [lein-ring "0.7.1"]
            [swank-clojure "1.4.2"]]
  :profiles {:dev {:resource-paths ["conf/test"]}}
  :aot [donkey.core]
  :main donkey.core
  :ring {:handler donkey.core/app
         :init donkey.core/load-configuration
         :port 31325}
  :iplant-rpm {:summary "iPlant Discovery Environment Business Layer Services"
               :provides "donkey"
               :dependencies ["iplant-service-config >= 0.1.0-5"]
               :config-files ["log4j.properties"]
               :config-path "conf/main"}
  :uberjar-exclusions [#"BCKEY.SF"]
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})
