(ns donkey.services.filesystem.metadata
  (:use [clojure-commons.error-codes] 
        [donkey.util.config]
        [donkey.util.validators]
        [donkey.services.filesystem.common-paths]
        [donkey.services.filesystem.validators]
        [clj-jargon.jargon]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]
            [cheshire.core :as json]
            [clojure.data.codec.base64 :as b64]
            [dire.core :refer [with-pre-hook! with-post-hook!]]
            [donkey.services.filesystem.validators :as validators]))

(defn- fix-unit
  [avu]
  (if (= (:unit avu) IPCRESERVED)
    (assoc avu :unit "")
    avu))

(defn- list-path-metadata
  [cm path]
  (filterv
   #(not= (:unit %) IPCSYSTEM)
   (map fix-unit (get-metadata cm (ft/rm-last-slash path)))))

(defn- reserved-unit
  "Turns a blank unit into a reserved unit."
  [avu-map]
  (if (string/blank? (:unit avu-map))
    IPCRESERVED
    (:unit avu-map)))

(defn metadata-get
  [user path]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-readable cm user path)
    {:metadata (list-path-metadata cm path)}))

(defn metadata-set
  [user path avu-map]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (when (= "failure" (:status avu-map))
      (throw+ {:error_code ERR_INVALID_JSON}))
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    (let [fixed-path (ft/rm-last-slash path)
          new-unit   (reserved-unit avu-map)
          attr       (:attr avu-map)
          value      (:value avu-map)]
      (log/warn "Fixed Path:" fixed-path)
      (log/warn "check" (true? (attr-value? cm fixed-path attr value)))
      (when-not (attr-value? cm fixed-path attr value)
        (log/warn "Adding " attr value "to" fixed-path)
        (set-metadata cm fixed-path attr value new-unit))
      {:path fixed-path :user user})))

(defn- encode-str
  [str-to-encode]
  (String. (b64/encode (.getBytes str-to-encode))))

(defn- workaround-delete
  "Gnarly workaround for a bug (I think) in Jargon. If a value
   in an AVU is formatted a certain way, it can't be deleted.
   We're base64 encoding the value before deletion to ensure
   that the deletion will work."
  [cm path attr]
  (let [{:keys [attr value unit]} (first (get-attribute cm path attr))]
    (set-metadata cm path attr (encode-str value) unit)))

(defn- metadata-batch-set
  [user path adds-dels]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    (let [new-path (ft/rm-last-slash path)]
      (doseq [del (:delete adds-dels)]
        (when (attribute? cm new-path del)
          (workaround-delete cm new-path del)
          (delete-metadata cm new-path del)))
      (doseq [avu (:add adds-dels)]
        (let [new-unit (reserved-unit avu)
              attr     (:attr avu)
              value    (:value avu)]
          (if-not (attr-value? cm new-path attr value)
            (set-metadata cm new-path attr value new-unit))))
      {:path new-path :user user})))

(defn metadata-delete
  [user path attr]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    (let [fix-unit #(if (= (:unit %1) IPCRESERVED) (assoc %1 :unit "") %1)
          avu      (map fix-unit (get-metadata cm (ft/rm-last-slash path)))]
      (workaround-delete cm path attr)
      (delete-metadata cm path attr))
    {:path path :user user}))

(defn- check-adds
  [adds]
  (mapv #(= (set (keys %)) (set [:attr :value :unit])) adds))

(defn- check-dels
  [dels]
  (mapv string? dels))

(defn do-metadata-get
  [{user :user path :path}]
  (metadata-get user path))

(with-pre-hook! #'do-metadata-get
  (fn [params]
    (log/warn "[call][do-metadata-get]" params)
    (validate-map params {:user string? :path string?})))

(with-post-hook! #'do-metadata-get (log-func "do-metadata-get"))

(defn do-metadata-set
  [{user :user path :path} body]
  (metadata-set user path body))

(with-pre-hook! #'do-metadata-set
  (fn [params body]
    (log/warn "[call][do-metadata-set]" params body)
    (validate-map params {:user string? :path string?})
    (validate-map body {:attr string? :value string? :unit string?})))

(with-post-hook! #'do-metadata-set (log-func "do-metadata-set"))

(defn do-metadata-batch-set
  [{user :user} {path :path :as body}]
  (metadata-batch-set user path body))

(with-pre-hook! #'do-metadata-batch-set
  (fn [params body]
    (log/warn "[call][do-metadata-set]" params body)
    (validate-map params {:user string? :path string?})
    (validate-map body {:add sequential? :delete sequential?})
    (let [user (:user params)
          path (:path params)
          adds (:add body)
          dels (:delete body)]
      (when (pos? (count adds))
        (if (not (every? true? (check-adds adds)))
          (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :field "add"})))
      (when (pos? (count dels))
        (if-not (every? true? (check-dels dels))
          (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :field "add"}))))))

(with-post-hook! #'do-metadata-batch-set (log-func "do-metadata-batch-set"))

(defn do-metadata-delete
  [{user :user path :path attr :attr}]
  (metadata-delete user path attr))

(with-pre-hook! #'do-metadata-delete
  (fn [params]
    (log/warn "[call][do-metadata-delete]" params)
    (validate-map params {:user string? :path string? :attr string?})))

(with-post-hook! #'do-metadata-delete (log-func "do-metadata-delete"))

