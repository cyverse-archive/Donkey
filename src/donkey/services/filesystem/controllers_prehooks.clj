(ns donkey.services.filesystem.controllers-prehooks
  (:use [clojure-commons.error-codes]
        [donkey.services.filesystem.common-paths]
        [donkey.services.filesystem.validators]
        [donkey.util.validators]
        [donkey.util.config]
        [donkey.services.filesystem.controllers]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [dire.core :refer [with-pre-hook!]]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]))

(with-pre-hook! #'do-get-csv-page
  (fn [params body]
    (log/warn "[call][do-get-csv-page]" params body)
    (validate-map params {:user string?})
    (validate-map
      body
      {:path       string?
       :delim      string?
       :chunk-size string?
       :page       string?})))

(with-pre-hook! #'do-read-csv-chunk
  (fn [params body]
    (log/warn "[call][do-read-csv-chunk]" params body)
    (validate-map params {:user string?})
    (validate-map body {:path        string? 
                        :position    string? 
                        :chunk-size  string? 
                        :line-ending string? 
                        :separator   string?})))


