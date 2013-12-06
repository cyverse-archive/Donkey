(ns donkey.services.filesystem.tickets
  (:use [clojure-commons.error-codes]
        [donkey.util.config]
        [donkey.util.validators]
        [donkey.services.filesystem.common-paths]
        [donkey.services.filesystem.validators]
        [clj-jargon.init :only [with-jargon]]
        [clj-jargon.tickets]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]
            [clostache.parser :as stache]
            [cheshire.core :as json]
            [dire.core :refer [with-pre-hook! with-post-hook!]]
            [donkey.services.filesystem.validators :as validators])
  (:import [java.util UUID]))

(defn- ticket-uuids?
  [cm user new-uuids]
  (try+
    (validators/all-tickets-nonexistant cm user new-uuids)
    true
    (catch error? e false)))

(defn- gen-uuids
  [cm user num-uuids]
  (let [new-uuids (doall (repeatedly num-uuids #(string/upper-case (str (UUID/randomUUID)))))]
    (if (ticket-uuids? cm user new-uuids)
      new-uuids
      (recur cm user num-uuids)) ))

(defn render-ticket-tmpl
  [cm ticket-map tmpl]
  (stache/render tmpl {:url       (kifshare-external-url)
                       :ticket-id (:ticket-id ticket-map)
                       :filename  (ft/basename (:path ticket-map))}))

(defn- add-tickets
  [user paths public?]
  (with-jargon (jargon-cfg) [cm]
    (let [new-uuids (gen-uuids cm user (count paths))]
      (validators/user-exists cm user)
      (validators/all-paths-exist cm paths)
      (validators/all-paths-writeable cm user paths)
      (doseq [[path uuid] (map list paths new-uuids)]
        (log/warn "[add-tickets] adding ticket for " path "as" uuid)
        (create-ticket cm (:username cm) path uuid)
        (when public?
          (log/warn "[add-tickets] making ticket" uuid "public")
          (doto (ticket-admin-service cm (:username cm))
            (.addTicketGroupRestriction uuid "public"))))
      (let [sysuser (:username cm)
            tmpl    (kifshare-download-template)]
        {:user    user
         :tickets (mapv
                   #(render-ticket-tmpl cm (ticket-map cm sysuser %) tmpl)
                   new-uuids)}))))

(defn- remove-tickets
  [user ticket-ids]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/all-tickets-exist cm user ticket-ids)
    (let [all-paths (mapv #(.getIrodsAbsolutePath (ticket-by-id cm (:username cm) %)) ticket-ids)]
      (validators/all-paths-writeable cm user all-paths)
      (doseq [ticket-id ticket-ids]
        (delete-ticket cm (:username cm) ticket-id))
      {:user user :tickets ticket-ids})))

(defn- list-tickets-for-paths
  [user paths]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/all-paths-exist cm paths)
    (validators/all-paths-readable cm user paths)
    {:tickets
     (apply merge (mapv #(hash-map %1 (ticket-ids-for-path cm (:username cm) %1)) paths))}))

(defn- check-tickets
  [tickets]
  (every? true? (mapv #(= (set (keys %)) (set [:path :ticket-id])) tickets)))

(defn do-add-tickets
  [{public :public user :user} {paths :paths}]
  (let [pub-param public
        public    (if (and public (= public "1")) true false)]
    (add-tickets user paths public)))

(with-pre-hook! #'do-add-tickets
  (fn [params body]
    (log/warn "[call][do-add-tickets]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (validate-num-paths (:paths body))))

(with-post-hook! #'do-add-tickets (log-func "do-add-tickets"))

(defn do-remove-tickets
  [{user :user} {tickets :tickets}]
  (remove-tickets user tickets))

(with-pre-hook! #'do-remove-tickets
  (fn [params body]
    (log/warn "[call][do-remove-tickets]" params body)
    (validate-map params {:user string?})
    (validate-map body {:tickets sequential?})
    (when-not (every? true? (mapv string? (:tickets body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field     "tickets"}))))

(with-post-hook! #'do-remove-tickets (log-func "do-remove-tickets"))

(defn do-list-tickets
  [{user :user} {paths :paths}]
  (list-tickets-for-paths user paths))

(with-pre-hook! #'do-list-tickets
  (fn [params body]
    (log/warn "[call][do-list-tickets]" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential?})
    (when-not (every? true? (mapv string? (:paths body)))
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field      "paths"}))
    (validate-num-paths (:paths body))))

(with-post-hook! #'do-list-tickets (log-func "do-list-tickets"))
