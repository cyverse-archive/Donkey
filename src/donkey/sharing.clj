(ns donkey.sharing
  (:use [clojure.data.json :only [json-str read-json]]
        [clojure.walk]
        [clojure.string :only [join]]
        [slingshot.slingshot :only [try+]]
        [donkey.config :only [nibblonian-base-url]]
        [donkey.service :only [build-url]]
        [donkey.transformers :only [add-current-user-to-url]]
        [donkey.user-attributes])
  (:require [clojure.tools.logging :as log]
            [clj-http.client :as client]
            [donkey.notifications :as dn]))

(defn nibblonian-url
  "Builds a URL to a Nibblonian service from the given relative URL path."
  [relative-url]
  (add-current-user-to-url (build-url (nibblonian-base-url) relative-url)))

(defn share-obj->nibb-share-req
  "Builds a Nibblonian request object from a share object."
  [path user_perms]
  {:paths (list path),
   :users (list (:user user_perms)),
   :permissions (:permissions user_perms)})

(defn unshare-obj->nibb-unshare-req
  "Builds a Nibblonian unshare request object from an unshare object."
  [unshare]
  {:paths (list (:path unshare)),
   :users (:users unshare)})

(defn foward-nibblonian-share
  "Forwards a Nibblonian share request."
  [path user_perms]
  (try+
    (client/post (nibblonian-url "share")
                 {:content-type :json
                  :body (json-str (share-obj->nibb-share-req path user_perms))
                  :throw-entire-message? true})
    (merge {:success true} user_perms)
    (catch map? e
      (log/error "nibblonian error: " e)
      (merge {:success false,
              :error (read-json (:body e))}
             user_perms))))

(defn foward-nibblonian-unshare
  "Parses an unshare object, which contains a path and a list of users,
   forwarding the path-users request to Nibblonian."
  [unshare]
  (try+
    (client/post (nibblonian-url "unshare")
                 {:content-type :json
                  :body (json-str (unshare-obj->nibb-unshare-req unshare))
                  :throw-entire-message? true})
    (merge {:success true} unshare)
    (catch map? e
      (log/error "nibblonian error: " e)
      (merge {:success false,
              :error (read-json (:body e))}
             unshare))))

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
  "Sends share notifications to both the current user and shared users."
  [path user_shares]
  (let [user (:shortUsername current-user)
        shared_with (map #(:user %) user_shares)
        subject (str path " has been shared.")
        error-message (str "unable to send share notification for " path)]
    (send-sharing-notification
      user
      subject
      (str path " has been shared with " (join ", " shared_with))
      error-message)
    (doall
      (map
        #(send-sharing-notification
           %
           subject
           (str user " has shared " path " with you.")
           error-message)
        shared_with))))

(defn- send-share-err-notification
  "Sends a share error notification to the current user."
  [path user_shares]
  (send-sharing-notification
    (:shortUsername current-user)
    (str "Could not share " path)
    (str path " could not be shared with "
         (join ", " (map #(:user %) user_shares)))
    (str "unable to send share error notification for " path)))

(defn- send-unshare-notifications
  "Sends an unshare notification to only the current user."
  [unshare]
  (let [path (:path unshare)]
    (send-sharing-notification
      (:shortUsername current-user)
      (str path " has been unshared.")
      (str path " has been unshared with " (join ", " (:users unshare)))
      (str "unable to send unshare notification for " path))))

(defn- send-unshare-err-notification
  "Sends an unshare error notification to the current user."
  [unshare]
  (let [path (:path unshare)]
    (send-sharing-notification
      (:shortUsername current-user)
      (str "Could not unshare " path)
      (str path " could not be unshared with " (join ", " (:users unshare)))
      (str "unable to send unshare error notification for " path))))

(defn- share-resource
  "Parses a share object, which contains a path and a list of users with
   permissions, forwarding each path-user-permission request to Nibblonian, then
   sends success notifications to the users involved, and any error
   notifications to the current user."
  [share]
  (let [path (:path share)
        user_share_results (map #(foward-nibblonian-share path %) (:users share))
        successful_shares (filter #(:success %) user_share_results)
        unsuccessful_shares (filter #(not (:success %)) user_share_results)]
    (when (seq successful_shares)
      (send-share-notifications path successful_shares))
    (when (seq unsuccessful_shares)
      (send-share-err-notification path unsuccessful_shares))
    {:path path :users user_share_results}))

(defn- unshare-resource
  "Forwards an unshare object to Nibblonian, which contains a path and a list of
   users, then sends success notifications to the users involved, and any error
   notifications to the current user."
  [unshare]
  (let [unshare_results (foward-nibblonian-unshare unshare)]
    (if (:success unshare_results)
      (send-unshare-notifications unshare_results)
      (send-unshare-err-notification unshare_results))
    unshare_results))

(defn share
  "Parses a batch share request, forwarding each path-user-permission request to
   Nibblonian."
  [req]
  (let [sharing (read-json (slurp (:body req)))]
    (walk #(share-resource %) #(json-str {:sharing %}) (:sharing sharing))))

(defn unshare
  "Parses a batch unshare request, forwarding each path-users request to
   Nibblonian."
  [req]
  (let [unshare (read-json (slurp (:body req)))]
    (walk #(unshare-resource %)
          #(json-str {:unshare %})
          (:unshare unshare))))

