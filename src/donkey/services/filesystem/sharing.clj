(ns donkey.services.filesystem.sharing
  (:use [clojure-commons.error-codes]
        [clojure-commons.validators]
        [donkey.util.config]
        [donkey.services.filesystem.common-paths]
        [donkey.services.filesystem.validators]
        [clj-jargon.init :only [with-jargon]]
        [clj-jargon.item-info :only [trash-base-dir is-dir?]]
        [clj-jargon.metadata]
        [clj-jargon.permissions]
        [clj-jargon.gen-query]
        [clj-jargon.listings]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]
            [cheshire.core :as json]
            [dire.core :refer [with-pre-hook! with-post-hook!]]
            [donkey.services.filesystem.validators :as validators]))

(def shared-with-attr "ipc-contains-obj-shared-with")

(defn- delete-avu
  "Deletes the provided AVU from the path."
  [cm fpath avu-map]
  (.deleteAVUMetadata (:collectionAO cm) fpath (map2avu avu-map)))

(defn- add-user-shared-with
  "Adds 'ipc-contains-obj-shared-with' AVU for a user to an object if it's not there."
  [cm fpath shared-with]
  (when (empty? (get-avus-by-collection cm fpath shared-with shared-with-attr))
    (set-metadata cm fpath shared-with shared-with shared-with-attr)))

(defn- remove-user-shared-with
  "Removes 'ipc-contains-obj-shared-with' AVU for a user from an object if it's there."
  [cm fpath shared-with]
  (when-not (empty? (get-avus-by-collection cm fpath shared-with shared-with-attr))
    (delete-metadata cm fpath shared-with)))

(defn- shared?
  ([cm share-with fpath]
     (:read (permissions cm share-with fpath)))
  ([cm share-with fpath desired-perms]
     (let [curr-perms (permissions cm share-with fpath)]
       (and (:read curr-perms) (= curr-perms desired-perms)))))

(defn- skip-share
  [user path reason]
  (log/warn "Skipping share of" path "with" user "because:" reason)
  {:user    user
   :path    path
   :reason  reason
   :skipped true})

(defn- share-path-home
  "Returns the home directory that a shared file is under."
  [share-path]
  (string/join "/" (take 4 (string/split share-path #"\/"))))

(defn- share-path
  "Shares a path with a user. This consists of the following steps:

       1. The parent directories up to the sharer's home directory need to be marked as readable
          by the sharee. Othwerwise, any files that are shared will be orphaned in the UI.

       2. If the shared item is a directory then the inherit bit needs to be set so that files
          that are uploaded into the directory will also be shared.

       3. The permissions are set on the item being shared. This is done recursively in case the
          item being shared is a directory."
  [cm user share-with {read-perm :read write-perm :write own-perm :own :as perms} fpath]
  (let [hdir      (share-path-home fpath)
        trash-dir (trash-base-dir cm user)
        base-dirs #{hdir trash-dir}]
    (log/warn fpath "is being shared with" share-with "by" user)
    (process-parent-dirs (partial set-readable cm share-with true) #(not (base-dirs %)) fpath)

    (when (is-dir? cm fpath)
      (log/warn fpath "is a directory, setting the inherit bit.")
      (.setAccessPermissionInherit (:collectionAO cm) (:zone cm) fpath true))

    (log/warn share-with "is being given read permissions on" hdir "by" user)
    (set-permissions cm share-with hdir true false false false)

    (set-permissions cm share-with fpath read-perm write-perm own-perm true)
    (log/warn
      share-with
      "is being given recursive permissions ("
      "read:" read-perm
      "write:" write-perm
      "own:" own-perm ")"
      "on" fpath)

    {:user share-with :path fpath}))

(defn- share-paths
  [cm user share-withs fpaths perms]
  (for [share-with share-withs
        fpath      fpaths]
    (cond (= user share-with)                 (skip-share share-with fpath :share-with-self)
          (in-trash? cm user fpath)           (skip-share share-with fpath :share-from-trash)
          (shared? cm share-with fpath perms) (skip-share share-with fpath :already-shared)
          :else                               (share-path cm user share-with perms fpath))))

(defn share
  [user share-withs fpaths perms]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/all-users-exist cm share-withs)
    (validators/all-paths-exist cm fpaths)
    (validators/user-owns-paths cm user fpaths)

    (let [keyfn      #(if (:skipped %) :skipped :succeeded)
          share-recs (group-by keyfn (share-paths cm user share-withs fpaths perms))
          sharees    (map :user (:succeeded share-recs))
          home-dir   (user-home-dir user)]
      (dorun (map (partial add-user-shared-with cm (user-home-dir user)) sharees))
      {:user        sharees
       :path        fpaths
       :skipped     (map #(dissoc % :skipped) (:skipped share-recs))
       :permissions perms})))

(defn- contains-subdir?
  [cm dpath]
  (some #(is-dir? cm %) (list-paths cm dpath)))

(defn- subdirs
  [cm dpath]
  (filter #(is-dir? cm %) (list-paths cm dpath)))

(defn- some-subdirs-readable?
  [cm user parent-path]
  (some #(is-readable? cm user %1) (subdirs cm parent-path)))

(defn- remove-inherit-bit?
  [cm user fpath]
  (empty? (remove (comp (conj (set (irods-admins)) user) :user)
                  (list-user-perms cm fpath))))

(defn- unshare-dir
  "Removes the inherit bit from a directory if the directory is no longer shared with any accounts
   other than iRODS administrative accounts."
  [cm user unshare-with fpath]
  (when (remove-inherit-bit? cm user fpath)
    (log/warn "Removing inherit bit on" fpath)
    (.setAccessPermissionToNotInherit (:collectionAO cm) (:zone cm) fpath true)))

(defn- unshare-path
  "Removes permissions for a user to access a path.  This consists of several steps:

       1. Remove the access permissions for the user.  This is done recursively in case the path
          being unshared is a directory.

       2. If the item being unshared is a directory, perform any directory-specific unsharing
          steps that are required.

       3. Remove the user's read permissions for parent directories in which the user no longer has
          access to any other files or subdirectories."
  [cm user unshare-with fpath]
  (let [base-dirs #{(ft/rm-last-slash (user-home-dir user)) (trash-base-dir cm user)}]
    (log/warn "Removing permissions on" fpath "from" unshare-with "by" user)
    (remove-permissions cm unshare-with fpath)

    (when (is-dir? cm fpath)
      (log/warn "Unsharing directory" fpath "from" unshare-with "by" user)
      (unshare-dir cm user unshare-with fpath))

    (log/warn "Removing read perms on parents of" fpath "from" unshare-with "by" user)
    (process-parent-dirs
      (partial set-readable cm unshare-with false)
      #(and (not (base-dirs %)) (not (contains-accessible-obj? cm unshare-with %)))
      fpath)
    {:user unshare-with :path fpath}))

(defn- unshare-paths
  [cm user unshare-withs fpaths]
  (for [unshare-with unshare-withs
        fpath        fpaths]
    (cond (= user unshare-with)           (skip-share unshare-with fpath :unshare-with-self)
          (shared? cm unshare-with fpath) (unshare-path cm user unshare-with fpath)
          :else                           (skip-share unshare-with fpath :not-shared))))

(defn- clean-up-unsharee-avus
  [cm fpath unshare-with]
  (when-not (shared? cm unshare-with fpath)
    (log/warn "Removing shared with AVU on" fpath "for" unshare-with)
    (remove-user-shared-with cm fpath unshare-with)))

(defn unshare
  "Allows 'user' to unshare file 'fpath' with user 'unshare-with'."
  [user unshare-withs fpaths]
  (log/debug "entered unshare")

  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/all-users-exist cm unshare-withs)
    (validators/all-paths-exist cm fpaths)
    (validators/user-owns-paths cm user fpaths)

    (log/debug "unshare - after validators")
    (log/debug "unshare - user: " user)
    (log/debug "unshare - unshare-withs: " unshare-withs)
    (log/debug "unshare - fpaths: " fpaths)

    (let [keyfn        #(if (:skipped %) :skipped :succeeded)
          unshare-recs (group-by keyfn (unshare-paths cm user unshare-withs fpaths))
          unsharees    (map :user (:succeeded unshare-recs))
          home-dir     (user-home-dir user)]
      (dorun (map (partial clean-up-unsharee-avus cm home-dir) unsharees))
      {:user unsharees
       :path fpaths
       :skipped (map #(dissoc % :skipped) (:skipped unshare-recs))})))

(defn- fix-username
  [username]
  (if (re-seq #"@" username)
    (subs username 0 (.indexOf username "@"))
    username))

(defn- boolean?
  [flag]
  (or (true? flag) (false? flag)))

(defn do-share
  [{user :user} {users :users paths :paths permissions :permissions}]
  (let [user        (fix-username user)
        share-withs (map fix-username users)]
    (share user share-withs paths permissions)))

(with-post-hook! #'do-share (log-func "do-share"))

(with-pre-hook! #'do-share
  (fn [params body]
    (log-call "do-share" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential? :users sequential? :permissions map?})
    (validate-map (:permissions body) {:read boolean? :write boolean? :own boolean?})
    (validate-num-paths (:paths body))))

(defn do-unshare
  [{user :user} {users :users paths :paths}]
  (let [user        (fix-username user)
        share-withs (map fix-username users)
        fpaths      paths]
    (unshare user share-withs fpaths)))

(with-pre-hook! #'do-unshare
  (fn [params body]
    (log-call "do-unshare" params body)
    (validate-map params {:user string?})
    (validate-map body {:paths sequential? :users sequential?})
    (validate-num-paths (:paths body))))

(with-post-hook! #'do-unshare (log-func "do-unshare"))
