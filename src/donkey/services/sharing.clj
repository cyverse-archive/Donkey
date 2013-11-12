(ns donkey.services.sharing
  (:use [clojure.walk]
        [clojure.string :only [join]]
        [slingshot.slingshot :only [try+]]
        [clojure-commons.file-utils :only [basename]]
        [donkey.util.config :only [nibblonian-base-url]]
        [donkey.util.service :only [build-url decode-stream]]
        [donkey.util.transformers :only [add-current-user-to-url]]
        [donkey.auth.user-attributes])
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]
            [clj-http.client :as client]
            [donkey.clients.notifications :as dn]
            [donkey.services.filesystem.sharing :as sh]))

(def file-list-threshold 10)

(defn- nibblonian-url
  "Builds a URL to a Nibblonian service from the given relative URL path."
  [relative-url]
  (add-current-user-to-url (build-url (nibblonian-base-url) relative-url)))

(defn- share-list->path-list
  "Converts a list of maps with path key-values to a list of path strings."
  [paths]
  (reduce #(conj %1 (:path %2)) [] paths))

(defn- path-list->file-list
  "Returns a string that joins the given path list by commas."
  [path-list]
  (join ", " (map basename path-list)))

(defn- build-nibblonian-share-req
  "Builds a Nibblonian request object from a username and a client share
   request."
  [user share]
  {:paths (list (:path share)),
   :users (list user),
   :permissions (:permissions share)})

(defn- build-nibblonian-unshare-req
  "Builds a Nibblonian unshare request object from a username and a path."
  [user path]
  {:paths (list path),
   :users (list user)})

(defn- forward-nibblonian-share
  "Forwards a Nibblonian share request."
  [user share]
  (let [paths       [(:path share)]
        sharer      (:shortUsername current-user)
        share-withs [user]
        perms       (:permissions share)]
    (try+
      (log/warn "share" paths "with" share-withs "by" sharer)
      (sh/share sharer share-withs paths perms)
      (merge {:success true} share)
      (catch map? e
        (log/error "nibblonian error: " e)
        (merge {:success false,
                :error (cheshire/decode (:body e) true)}
               share)))))

(defn- forward-nibblonian-unshare
  "Forwards a Nibblonian unshare request."
  [user path]
  (let [unsharer      (:shortUsername current-user)
        unshare-withs [user]]
    (try+
      (log/warn "unshare" path "from" user "by" unsharer)
      (sh/unshare unsharer unshare-withs (vector path))
      {:success true
       :path path}
      (catch map? e
        (log/error "nibblonian error: " e)
        {:success false,
         :error (cheshire/decode (:body e) true)
         :path path}))))

(defn- send-sharing-notification
  "Sends an (un)sharing notification."
  [user subject message action path-list error-message]
  (log/debug "sending sharing notification to" user ":" subject)
  (try
    (dn/send-notification {:type "data"
                           :user user
                           :subject subject
                           :message message
                           :payload {:action action
                                     :paths path-list}})
    (catch Exception e
      (log/warn e error-message))))

(defn- send-share-notifications
  "Sends share notifications to both the current user and shared user."
  [sharee shares]
  (let [sharer (:shortUsername current-user)
        path-list (share-list->path-list shares)
        share-count (count path-list)
        file-list (path-list->file-list path-list)
        sharer-summary (str share-count
                            " file(s)/folder(s) have been shared with "
                            sharee)
        sharer-notification (if (< share-count file-list-threshold)
                              (str "The following file(s)/folder(s) have been shared with "
                                   sharee ": "
                                   file-list)
                              sharer-summary)
        sharee-summary (str sharer
                            " has shared "
                            share-count
                            " file(s)/folder(s) with you.")
        sharee-notification (if (< share-count file-list-threshold)
                              (str sharer
                                   " has shared the following file(s)/folder(s) with you: "
                                   file-list)
                              sharee-summary)]
    (send-sharing-notification
      sharer
      sharer-summary
      sharer-notification
      "share"
      path-list
      (str "unable to send share notification to " sharer " for " sharee))
    (send-sharing-notification
      sharee
      sharee-summary
      sharee-notification
      "share"
      path-list
      (str "unable to send share notification from " sharer " to " sharee))))

(defn- send-share-err-notification
  "Sends a share error notification to the current user."
  [sharee shares]
  (let [path-list (share-list->path-list shares)
        share-count (count path-list)
        file-list (path-list->file-list path-list)
        subject (str share-count
                     " file(s)/folder(s) could not be shared with "
                     sharee)
        notification (if (< share-count file-list-threshold)
                       (str "The following file(s)/folder(s) could not be shared with "
                            sharee ": "
                            file-list)
                       subject)]
    (send-sharing-notification
      (:shortUsername current-user)
      subject
      notification
      "share"
      path-list
      (str "unable to send share error notification for " sharee))))

(defn- send-unshare-notifications
  "Sends an unshare notification to only the current user."
  [unsharee unshares]
  (let [path-list (share-list->path-list unshares)
        share-count (count path-list)
        file-list (path-list->file-list path-list)
        subject (str share-count
                     " file(s)/folder(s) have been unshared with "
                     unsharee)
        notification (if (< share-count file-list-threshold)
                       (str " The following file(s)/folder(s) have been unshared with "
                            unsharee ": "
                            file-list)
                       subject)]
    (send-sharing-notification
      (:shortUsername current-user)
      subject
      notification
      "unshare"
      path-list
      (str "unable to send unshare notification for " unsharee))))

(defn- send-unshare-err-notification
  "Sends an unshare error notification to the current user."
  [unsharee unshares]
  (let [path-list (share-list->path-list unshares)
        share-count (count path-list)
        file-list (path-list->file-list path-list)
        subject (str share-count
                     " file(s)/folder(s) could not be unshared with "
                     unsharee)
        notification (if (< share-count file-list-threshold)
                       (str "The following file(s)/folder(s) could not be unshared with "
                            unsharee ": "
                            file-list)
                       subject)]
    (send-sharing-notification
      (:shortUsername current-user)
      subject
      notification
      "unshare"
      path-list
      (str "unable to send unshare error notification for " unsharee))))

(defn- share-with-user
  "Forwards share requests to Nibblonian from the user and list of paths and
   permissions in the given share map, sending any success notifications to the
   users involved, and any error notifications to the current user."
  [share]
  (let [user (:user share)
        paths (:paths share)
        user_share_results (map #(forward-nibblonian-share user %) paths)
        successful_shares (filter :success user_share_results)
        unsuccessful_shares (remove :success user_share_results)]
    (when (seq successful_shares)
      (send-share-notifications user successful_shares))
    (when (seq unsuccessful_shares)
      (send-share-err-notification user unsuccessful_shares))
    {:user user :sharing user_share_results}))

(defn- unshare-with-user
  "Forwards unshare requests to Nibblonian from the user and list of paths in
   the given unshare map, sending any success notifications to the users
   involved, and any error notifications to the current user."
  [unshare]
  (let [user (:user unshare)
        paths (:paths unshare)
        unshare_results (map #(forward-nibblonian-unshare user %) paths)
        successful_unshares (filter :success unshare_results)
        unsuccessful_unshares (remove :success unshare_results)]
    (when (seq successful_unshares)
      (send-unshare-notifications user successful_unshares))
    (when (seq unsuccessful_unshares)
      (send-unshare-err-notification user unsuccessful_unshares))
    {:user user :unshare unshare_results}))

(defn share
  "Parses a batch share request, forwarding each user-share request to
   Nibblonian."
  [req]
  (let [sharing (decode-stream (:body req))]
    (walk share-with-user
          #(cheshire/encode {:sharing %})
          (:sharing sharing))))

(defn unshare
  "Parses a batch unshare request, forwarding each user-unshare request to
   Nibblonian."
  [req]
  (let [unshare (decode-stream (:body req))]
    (walk unshare-with-user
          #(cheshire/encode {:unshare %})
          (:unshare unshare))))
