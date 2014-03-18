(ns donkey.services.filesystem.metadata
  (:use [clojure-commons.error-codes]
        [clojure-commons.validators]
        [donkey.util.config]
        [donkey.services.filesystem.common-paths]
        [donkey.services.filesystem.validators]
        [clj-jargon.init :only [with-jargon]]
        [clj-jargon.metadata]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]
            [cheshire.core :as json]
            [clojure.data.codec.base64 :as b64]
            [dire.core :refer [with-pre-hook! with-post-hook!]]
            [donkey.services.filesystem.validators :as validators]))

(defn- fix-unit
  "Used to replace the IPCRESERVED unit with an empty string."
  [avu]
  (if (= (:unit avu) IPCRESERVED)
    (assoc avu :unit "")
    avu))

(defn- list-path-metadata
  "Returns the metadata for a path. Passes all AVUs to (fix-unit).
   AVUs with a unit matching IPCSYSTEM are filtered out."
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
  "Returns the metadata for a path. Filters out system AVUs and replaces
   units set to ipc-reserved with an empty string."
  [user path]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-readable cm user path)
    {:metadata (list-path-metadata cm path)}))

(defn- common-metadata-set
  "Adds an AVU to 'path'. The AVU is passed in as a map in the format:
   {
      :attr attr-string
      :value value-string
      :unit unit-string
   }
   It's a no-op if an AVU with the same attribute and value is already
   associated with the path."
  [cm path avu-map]
  (let [fixed-path (ft/rm-last-slash path)
        new-unit   (reserved-unit avu-map)
        attr       (:attr avu-map)
        value      (:value avu-map)]
    (log/warn "Fixed Path:" fixed-path)
    (log/warn "check" (true? (attr-value? cm fixed-path attr value)))
    (when-not (attr-value? cm fixed-path attr value)
      (log/warn "Adding " attr value "to" fixed-path)
      (add-metadata cm fixed-path attr value new-unit))
    fixed-path))

(defn metadata-set
  "Allows user to set metadata on a path. The user must exist in iRODS
   and have write permissions on the path. The path must exist. The
   avu-map parameter must be in this format:
   {
      :attr attr-string
      :value value-string
      :unit unit-string
   }"
  [user path avu-map]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (when (= "failure" (:status avu-map))
      (throw+ {:error_code ERR_INVALID_JSON}))
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    {:path (common-metadata-set cm path avu-map)
     :user user}))

(defn admin-metadata-set
  "Adds the AVU to path, bypassing user permission checks. See (metadata-set)
   for the AVU map format."
  [path avu-map]
  (with-jargon (jargon-cfg) [cm]
    (when (= "failure" (:status avu-map))
      (throw+ {:error_code ERR_INVALID_JSON}))
    (validators/path-exists cm path)
    (validators/path-writeable cm (irods-user) path)
    (common-metadata-set cm path avu-map)))

(defn- encode-str
  "Returns str-to-encode as a base 64 encoded string."
  [str-to-encode]
  (String. (b64/encode (.getBytes str-to-encode))))

(defn- workaround-delete
  "Gnarly workaround for a bug (I think) in Jargon. If a value
   in an AVU is formatted a certain way, it can't be deleted.
   We're base64 encoding the value before deletion to ensure
   that the deletion will work."
  [cm path attr value]
  (let [{:keys [attr value unit]} (first (get-attribute-value cm path attr value))
        new-val (encode-str value)]
    (add-metadata cm path attr new-val unit)
    new-val))

(defn- metadata-batch-set
  "Adds and deletes metadata on path for a user. add-dels should be in the
   following format:
   {
      :delete [{:attr :value :unit}]
      :add [{:attr :value :unit}]
   }
   All value in the maps should be strings, just like with (metadata-set)."
  [user path adds-dels]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    (let [new-path (ft/rm-last-slash path)]
      (doseq [del (:delete adds-dels)]
        (let [attr  (:attr del)
              value (:value del)]
          (if (attr-value? cm new-path attr value)
            (delete-metadata cm new-path attr value))))
      (doseq [avu (:add adds-dels)]
        (let [new-unit (reserved-unit avu)
              attr     (:attr avu)
              value    (:value avu)]
          (if-not (attr-value? cm new-path attr value)
            (add-metadata cm new-path attr value new-unit))))
      {:path new-path :user user})))

(defn metadata-delete
  "Deletes an AVU from path on behalf of a user. attr and value should be strings."
  [user path attr value]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    (delete-metadata cm path attr value)
    {:path path :user user}))

(defn- check-avus
  [adds]
  (mapv
   #(and (map? %1)
         (contains? %1 :attr)
         (contains? %1 :value)
         (contains? %1 :unit))
   adds))

(defn do-metadata-get
  "Entrypoint for the API. Calls (metadata-get). Parameter should be a map
   with :user and :path as keys. Values are strings."
  [{user :user path :path}]
  (metadata-get user path))

(with-pre-hook! #'do-metadata-get
  (fn [params]
    (log-call "do-metadata-get" params)
    (validate-map params {:user string? :path string?})))

(with-post-hook! #'do-metadata-get (log-func "do-metadata-get"))

(defn do-metadata-set
  "Entrypoint for the API. Calls (metadata-set). Parameter should be a map
   with :user and :path as keys. Values are strings."
  [{user :user path :path} body]
  (metadata-set user path body))

(with-pre-hook! #'do-metadata-set
  (fn [params body]
    (log-call "do-metadata-set" params body)
    (validate-map params {:user string? :path string?})
    (validate-map body {:attr string? :value string? :unit string?})))

(with-post-hook! #'do-metadata-set (log-func "do-metadata-set"))

(defn do-metadata-batch-set
  "Entrypoint for the API that calls (metadata-batch-set). Parameter is a map
   with :user and :path as keys. Values are strings."
  [{user :user path :path} body]
  (metadata-batch-set user path body))

(with-pre-hook! #'do-metadata-batch-set
  (fn [params body]
    (log-call "do-metadata-batch-set" params body)
    (validate-map params {:user string? :path string?})
    (validate-map body {:add sequential? :delete sequential?})
    (let [user (:user params)
          path (:path params)
          adds (:add body)
          dels (:delete body)]
      (log/warn (jargon-cfg))
      (when (pos? (count adds))
        (if-not (every? true? (check-avus adds))
          (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :field "add"})))
      (when (pos? (count dels))
        (if-not (every? true? (check-avus dels))
          (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :field "delete"}))))))

(with-post-hook! #'do-metadata-batch-set (log-func "do-metadata-batch-set"))

(defn do-metadata-delete
  "Entrypoint for the API that calls (metadata-delete). Parameter is a map
   with :user, :path, :attr, :value as keys. Values are strings."
  [{user :user path :path attr :attr value :value}]
  (metadata-delete user path attr value))

(with-pre-hook! #'do-metadata-delete
  (fn [params]
    (log-call "do-metadata-delete" params)
    (validate-map params {:user string?
                          :path string?
                          :attr string?
                          :value string?})))

(with-post-hook! #'do-metadata-delete (log-func "do-metadata-delete"))
