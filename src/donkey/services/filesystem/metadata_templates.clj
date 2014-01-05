(ns donkey.services.filesystem.metadata-templates
  (:use [donkey.services.filesystem.common-paths]
        [korma.core]
        [korma.db :only [with-db]])
  (:require [dire.core :refer [with-pre-hook! with-post-hook!]]
            [donkey.util.db :as db]
            [donkey.util.service :as service])
  (:import [java.util UUID]))

(defn- list-metadata-templates
  []
  (with-db db/de
    (select :metadata_templates
            (fields :id :name)
            (where {:deleted false}))))

(defn- get-metadata-template-name
  [id]
  (if-let [template-name (:name (first (select :metadata_templates (where {:id id}))))]
    template-name
    (service/not-found "metadata template" id)))

(defn- list-metadata-template-attributes
  [id]
  (select [:metadata_attributes :attr]
          (join [:metadata_value_types :value_type] {:attr.value_type_id :value_type.id})
          (fields [:attr.value_type_id :id]
                  [:attr.name :name]
                  [:attr.description :description]
                  [:value_type.name :type])
          (order [:attr.display_order])))

(defn- view-metadata-template
  [id]
  (with-db db/de
    {:id         id
     :name       (get-metadata-template-name id)
     :attributes (list-metadata-template-attributes id)}))

(defn do-metadata-template-list
  []
  {:metadata_templates (list-metadata-templates)})

(with-pre-hook! #'do-metadata-template-list
  (fn []
    (log-call "do-metadata-template-list")))

(with-post-hook! #'do-metadata-template-list (log-func "do-metadata-template-list"))

(defn do-metadata-template-view
  [id]
  (view-metadata-template (UUID/fromString id)))

(with-pre-hook! #'do-metadata-template-view
  (fn [id]
    (log-call "do-metadata-template-view" id)))

(with-post-hook! #'do-metadata-template-view (log-func "do-metadata-template-view"))
