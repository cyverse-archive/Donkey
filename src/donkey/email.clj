(ns donkey.email
  (:use [donkey.user-attributes :only [current-user]])
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.string :as string]
            [donkey.config :as config]))

(defn send-email
  "Sends an e-mail message via the iPlant e-mail service."
  [& {:keys [to from-addr from-name subject template values]}]
  (client/post
   (config/iplant-email-base-url)
   {:content-type :json
    :body         (cheshire/encode {:to        to
                                    :from-addr from-addr
                                    :from-name from-name
                                    :subject   subject
                                    :template  template
                                    :values    values})}))

(defn send-tool-request-email
  "Sends the email message informing Core Services of a tool request."
  [tool-req {:keys [firstname lastname]}]
  (send-email
   :to        (config/tool-request-dest-addr)
   :from-addr (config/tool-request-src-addr)
   :subject   "New Tool Request"
   :template  "tool_request"
   :values    {:username           (str firstname " " lastname)
               :environment        (config/environment-name)
               :toolrequestid      (:uuid tool-req)
               :toolrequestdetails (cheshire/encode tool-req {:pretty true})}))
