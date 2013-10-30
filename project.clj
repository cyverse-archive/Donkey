(defproject donkey "2.0.0-SNAPSHOT"
  :description "Framework for hosting DiscoveryEnvironment metadata services."
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.novemberain/langohr "1.4.0"]
                 [org.clojure/core.memoize "0.5.3"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/java.classpath "0.2.0"]
                 [org.apache.tika/tika-core "1.3"]
                 [org.iplantc/clj-cas "1.0.1-SNAPSHOT"]
                 [org.iplantc/clj-jargon "0.2.13-SNAPSHOT"
                  :exclusions [[xerces/xmlParserAPIs]
                               [org.irods.jargon.transfer/jargon-transfer-dao-spring]]]
                 [org.iplantc/clojure-commons "1.4.6-SNAPSHOT"]
                 [org.iplantc/deliminator "0.1.0-SNAPSHOT"]
                 [org.iplantc/mescal "0.1.0-SNAPSHOT"]
                 [org/forester "1.005" ]
                 [org.nexml.model/nexml "1.5-SNAPSHOT"]
                 [net.sf.json-lib/json-lib "2.4" :classifier "jdk15"]
                 [cheshire "5.0.1"]
                 [clj-http "0.7.7"]
                 [com.cemerick/url "0.0.7"]
                 [compojure "1.0.1"]
                 [heuristomancer "0.1.1-SNAPSHOT"]
                 [ring/ring-jetty-adapter "1.0.1"]
                 [slingshot "0.10.1"]
                 [clojurewerkz/elastisch "1.0.2"]
                 [hoot "0.1.0-SNAPSHOT"]
                 [com.novemberain/validateur "1.4.0"]
                 [xerces/xercesImpl "2.11.0"]
                 [commons-net "3.3"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [net.sf.opencsv/opencsv "2.0"]
                 [com.novemberain/langohr "1.4.1"]
                 [clj-icat-direct "0.0.1"]]
  :plugins [[org.iplantc/lein-iplant-rpm "1.4.3-SNAPSHOT"]
            [lein-ring "0.7.4"]
            [swank-clojure "1.4.2"]]
  :profiles {:dev {:resource-paths ["conf/test"]}}
  :aot [donkey.core]
  :main donkey.core
  :ring {:handler donkey.core/app
         :init donkey.core/lein-ring-init
         :port 31325}
  :iplant-rpm {:summary "iPlant Discovery Environment Business Layer Services"
               :provides "donkey"
               :dependencies ["iplant-service-config >= 0.1.0-5" "iplant-clavin" "java-1.7.0-openjdk"]
               :exe-files ["resources/scripts/filetypes/guess-2.pl"]
               :config-files ["log4j.properties"]
               :config-path "conf/main"}
  :uberjar-exclusions [#"BCKEY.SF" #"LICENSE" #"NOTICE"]
  :repositories [["iplantCollaborative"
                  "http://projects.iplantcollaborative.org/archiva/repository/internal/"]
                 ["biojava"
                  "http://www.biojava.org/download/maven"]
                 ["nexml"
                  {:url "http://nexml-dev.nescent.org/.m2/repository"
                   :checksum :ignore
                   :update :daily}]])
