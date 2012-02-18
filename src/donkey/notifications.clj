(ns donkey.notifications
  (:use [clojure.data.json :only (json-str read-json)])
  (:require [clojure.tools.logging :as log]))

(defn- get-app
  "Gets an app from the database."
  [app-id app-retriever]
  (if (nil? app-id)
    nil
    (try 
      (.getTransformationActivity app-retriever app-id)
      (catch Exception e nil))))

(defn- get-app-description
  "Gets an app description from the database."
  [app-id app-retriever]
  (log/debug "looking up the description for app" app-id)
  (let [app (get-app app-id app-retriever)]
    (if (nil? app) "" (.getDescription app))))

(defn- add-app-details-to-message
  "Adds application details to a single message."
  [msg app-retriever]
  (let [app-id (get-in msg [:payload :analysis_id])]
    (assoc-in msg [:payload :analysis-details]
              (get-app-description app-id app-retriever))))

(defn- add-app-details-to-messages
  "Adds application details to a list of messages."
  [msgs app-retriever]
  (map #(add-app-details-to-message % app-retriever) msgs))

(defn- add-app-details-to-map
  "Adds application details to a map."
  [m app-retriever]
  {:messages (add-app-details-to-messages (:messages m) app-retriever)})

(defn add-app-details
  "Adds application details to notifications in a response from the
   notification agent."
  [res app-retriever]
  (let [m (read-json (:body res))]
    (log/debug "adding app details to notifications:" m)
    (assoc res :body (json-str (add-app-details-to-map m app-retriever)))))
