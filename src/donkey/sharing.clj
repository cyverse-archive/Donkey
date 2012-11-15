(ns donkey.sharing
  (:use [clojure.data.json :only [json-str read-json]]
        [clojure.walk]
        [clojure.string :only [join]]
        [slingshot.slingshot :only [try+]]
        [clojure-commons.file-utils :only [basename]]
        [donkey.config :only [nibblonian-base-url]]
        [donkey.service :only [build-url]]
        [donkey.transformers :only [add-current-user-to-url]]
        [donkey.user-attributes])
  (:require [clojure.tools.logging :as log]
            [clj-http.client :as client]
            [donkey.notifications :as dn]))

(defn- nibblonian-url
  "Builds a URL to a Nibblonian service from the given relative URL path."
  [relative-url]
  (add-current-user-to-url (build-url (nibblonian-base-url) relative-url)))

(defn- paths-map->path-list
  "Converts a paths map to a list of path strings."
  [paths]
  (reduce #(conj %1 (:path %2)) [] paths))

(defn- path-list->file-list
  "Returns the count of path strings in the given list and a string
   representaion of the list. The second string value may be a string that joins
   the list by commas, or a string that just includes the count of paths if
   there are more than a certain number."
  [path-list]
  (let [share-count (count path-list)
        file-list (if (>= share-count 10)
                    (str share-count " file(s)/folder(s)")
                    (->> path-list
                      (map #(basename %))
                      (join ", ")))]
    [share-count file-list]))

(defn- share-obj->nibb-share-req
  "Builds a Nibblonian request object from a username, permissions map, and a
   list of path maps."
  [user permissions paths]
  {:paths (paths-map->path-list paths),
   :users (list user),
   :permissions permissions})

(defn- unshare-obj->nibb-unshare-req
  "Builds a Nibblonian unshare request object from a client unshare request."
  [unshare]
  {:paths (:paths unshare),
   :users (list (:user unshare))})

(defn- foward-nibblonian-share
  "Forwards a Nibblonian share request."
  [user [permissions paths]]
  (let [body (json-str (share-obj->nibb-share-req user permissions paths))]
    (log/debug "foward-nibblonian-share: " body)
    (try+
      (client/post (nibblonian-url "share")
                   {:content-type :json
                    :body body
                    :throw-entire-message? true})
      {:success true :paths paths}
      (catch map? e
        (log/error "nibblonian error: " e)
        {:success false,
         :error (read-json (:body e))
         :paths paths}))))

(defn- foward-nibblonian-unshare
  "Forwards a Nibblonian unshare request from a client unshare request."
  [unshare]
  (let [body (json-str (unshare-obj->nibb-unshare-req unshare))]
    (log/debug "foward-nibblonian-unshare: " body)
    (try+
      (client/post (nibblonian-url "unshare")
                   {:content-type :json
                    :body body
                    :throw-entire-message? true})
      (merge {:success true} unshare)
      (catch map? e
        (log/error "nibblonian error: " e)
        (merge {:success false,
                :error (read-json (:body e))}
               unshare)))))

(defn- send-sharing-notification
  "Sends an (un)sharing notification."
  [user subject message error-message]
  (log/debug "sending sharing notification to" user ":" subject)
  (try
    (dn/send-notification {:type "data"
                           :user user
                           :subject subject
                           :message message})
    (catch Exception e
      (log/warn e error-message))))

(defn- send-share-notifications
  "Sends share notifications to both the current user and shared user."
  [sharee shares]
  (let [sharer (:shortUsername current-user)
        path-list (reduce #(concat %1 (paths-map->path-list (:paths %2)))
                          []
                          shares)
        [share-count file-list] (path-list->file-list path-list)
        subject (str share-count " file(s)/folder(s) have been shared.")]
    (send-sharing-notification
      sharer
      subject
      (str "The following file(s)/folder(s) have been shared with "
           sharee ": "
           file-list)
      (str "unable to send share notification to " sharer " for " sharee))
    (send-sharing-notification
      sharee
      subject
      (str sharer " has shared the following file(s)/folder(s) with you: "
           file-list)
      (str "unable to send share notification from " sharer " to " sharee))))

(defn- send-share-err-notification
  "Sends a share error notification to the current user."
  [sharee shares]
  (let [path-list (reduce
                    #(concat %1 (paths-map->path-list (:paths %2)))
                    []
                    shares)
        [share-count file-list] (path-list->file-list path-list)]
    (send-sharing-notification
      (:shortUsername current-user)
      (str "Could not share " share-count " file(s)/folder(s) with " sharee)
      (str "The following file(s)/folder(s) could not be shared with "
           sharee ": "
           file-list)
      (str "unable to send share error notification for " sharee))))

(defn- send-unshare-notifications
  "Sends an unshare notification to only the current user."
  [unshare]
  (let [unsharee (:user unshare)
        [share-count file-list] (path-list->file-list (:paths unshare))]
    (send-sharing-notification
      (:shortUsername current-user)
      (str share-count " file(s)/folder(s) have been unshared with " unsharee)
      (str " The following file(s)/folder(s) have been unshared with "
           unsharee ": "
           file-list)
      (str "unable to send unshare notification for " unsharee))))

(defn- send-unshare-err-notification
  "Sends an unshare error notification to the current user."
  [unshare]
  (let [unsharee (:user unshare)
        [share-count file-list] (path-list->file-list (:paths unshare))]
    (send-sharing-notification
      (:shortUsername current-user)
      (str "Could not unshare " share-count " file(s)/folder(s) with " unsharee)
      (str "The following file(s)/folder(s) could not be unshared with "
           unsharee ": "
           file-list)
      (str "unable to send unshare error notification for " unsharee))))

(defn- share-with-user
  "Parses a share map, which is keyed by a username to an inner map keyed by
   permissions to arrays of paths. Each user-permission-paths request is
   forwarded to Nibblonian, then success notifications are sent to the users
   involved, and any error notifications to the current user."
  [[user shares]]
  (let [user_share_results (map #(foward-nibblonian-share user %) shares)
        successful_shares (filter #(:success %) user_share_results)
        unsuccessful_shares (remove #(:success %) user_share_results)]
    (when (seq successful_shares)
      (send-share-notifications user successful_shares))
    (when (seq unsuccessful_shares)
      (send-share-err-notification user unsuccessful_shares))
    {:user user :sharing user_share_results}))

(defn- unshare-with-user
  "Forwards an unshare object to Nibblonian, which contains a user and a list of
   paths, then sends success notifications to the users involved, and any error
   notifications to the current user."
  [unshare]
  (let [unshare_results (foward-nibblonian-unshare unshare)]
    (if (:success unshare_results)
      (send-unshare-notifications unshare_results)
      (send-unshare-err-notification unshare_results))
    unshare_results))

(defn- group-share-by-permissions
  "Groups a user-share request by permission settings."
  [share]
  (let [user (:user share)
        paths (:paths share)
        shares (group-by :permissions paths)]
    (log/debug "grouping share by permission:\n" shares)
    {user shares}))

(defn share
  "Parses a batch share request, forwarding each user-share request to
   Nibblonian."
  [req]
  (let [sharing (read-json (slurp (:body req)))
        sharing (map #(group-share-by-permissions %) (:sharing sharing))]
    (log/debug "shares grouped by permissions:\n" sharing)
    (walk #(share-with-user (first %))
          #(json-str {:sharing %})
          sharing)))

(defn unshare
  "Parses a batch unshare request, forwarding each user-paths request to
   Nibblonian."
  [req]
  (let [unshare (read-json (slurp (:body req)))]
    (walk #(unshare-with-user %)
          #(json-str {:unshare %})
          (:unshare unshare))))
