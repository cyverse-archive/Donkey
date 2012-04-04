(ns donkey.user-sessions
  (:use [slingshot.slingshot :only [try+ throw+]]
        [clojure-commons.error-codes]
        [donkey.config])
  (:require [clj-http.client :as cl]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.file-utils :as ft]))

(defn key-url
  [user]
  (str 
    (string/join "/" 
      (map ft/rm-last-slash [(riak-base-url) (riak-sessions-bucket) user]))
    "?returnbody=true") )

(defn user-session
  ([user]
    (log/warn (str "user-session: GET " (key-url user)))
    (let [resp (cl/get (key-url user) {:throw-exceptions false})]
      (cond
        (= 200 (:status resp)) (:body resp)
        (= 404 (:status resp)) "{}"
        :else (throw+ {:error_code ERR_REQUEST_FAILED
                       :body (:body resp)}))))
  
  ([user new-session]
    (log/warn (str "user-session: POST " (key-url user) " " new-session))
    (let [resp (cl/post 
                 (key-url user) 
                 {:content-type :json :body new-session} 
                 {:throw-exceptions false})]
        (cond
          (= 200 (:status resp)) (:body resp)
          :else                  (throw+ {:error_code ERR_REQUEST_FAILED 
                                          :body (:body resp)})))))