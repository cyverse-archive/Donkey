(ns donkey.services.garnish.messages
  (:use [donkey.services.garnish.irods])
  (:require [clojure.tools.logging :as log]
            [donkey.clients.amqp :as amqp]))

(defn filetype-handler
  [payload]
  (log/warn (format "[donkey] Message: %s" payload)))
