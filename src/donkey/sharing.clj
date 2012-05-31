(ns donkey.sharing
  (:use [clojure.data.json :only [json-str read-json]]
        [clojure.walk]
        [slingshot.slingshot :only [try+]]
        [donkey.config :only [nibblonian-base-url]]
        [donkey.service :only [build-url json-content-type]]
        [donkey.transformers :only [add-current-user-to-url]])
  (:require [clojure.tools.logging :as log]
            [clj-http.client :as client]))

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

(defn foward-nibblonian-share
  "Forwards a Nibblonian share request."
  [path user_perms]
  (try+
    (client/post (nibblonian-url "share")
                 {:content-type json-content-type
                  :body (json-str (share-obj->nibb-share-req path user_perms))
                  :throw-entire-message? true})
    (merge {:success true} user_perms)
    (catch map? e
      (log/error "nibblonian error: " e)
      (merge {:success false,
              :error (read-json (:body e))}
             user_perms))))

(defn walk-share
  "Parses a share object, which contains a path and a list of users with
   permissions, forwarding each path-user-permission request to Nibblonian."
  [share]
  (let [path (:path share)]
    (walk #(foward-nibblonian-share path %)
          #(hash-map :path path :users %)
          (:users share))))

(defn share
  "Parses a batch share request, forwarding each path-user-permission request to
   Nibblonian."
  [req]
  (let [sharing (read-json (slurp (:body req)))]
    (walk #(walk-share %) #(json-str {:sharing %}) (:sharing sharing))))
