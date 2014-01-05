(ns donkey.services.filesystem.metadata-templates
  (:use [donkey.services.filesystem.common-paths]
        [korma.core]
        [korma.db :only [with-db]])
  (:require [dire.core :refer [with-pre-hook! with-post-hook!]]
            [donkey.util.db :as db]))

(defn do-metadata-template-list
  [params]
  (with-db db/de
    {:metadata_templates
     (select :metadata_templates
             (fields :id :name)
             (where {:deleted false}))}))

(with-pre-hook! #'do-metadata-template-list
  (fn [params]
    (log-call "do-metadata-template-list" params)))

(with-post-hook! #'do-metadata-template-list (log-func "do-metadata-template-list"))
