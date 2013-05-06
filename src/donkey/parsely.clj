(ns donkey.parsely
  (:require [donkey.service :as svc]
            [donkey.config :as cfg]))

(defn triples
  [req params]
  (svc/forward-get (svc/build-url-with-query (cfg/parsely-url) params "triples") req))

