(ns donkey.util.messaging
  (:require [donkey.services.garnish.messages :as ftype]
            [donkey.clients.amqp :as amqp]
            [donkey.util.config :as cfg]
            [clojure.tools.logging :as log]
            [clojure-commons.error-codes :as ce]))

(defn dataobject-added
  "Event handler for 'data-object.added' events."
  [^bytes payload]
  (ftype/filetype-message-handler (String. payload "UTF-8")))

(defn message-handler
  "A langohr compatible message callback. This will push out message handling to other functions
   based on the value of the routing-key. This will allow us to pull in the full iRODS event
   firehose later and delegate execution to handlers without having to deal with AMQPs object
   hierarchy.

   The payload is passed to handlers as a byte stream. That should theoretically give us the
   ability to handle binary data arriving in messages, even though that doesn't seem likely."
  [channel {:keys [routing-key content-type delivery-tag type] :as meta} ^bytes payload]
  (log/warn (format "[message-handler] [%s] [%s]" routing-key (String. payload "UTF-8")))
  (case routing-key
    "data-object.added" (dataobject-added payload)
    nil))

(defn- receive
  []
  (try
    (amqp/configure message-handler)
    (catch Exception e
      (log/error "[messaging-initialization]" (ce/format-exception e)))))

(defn- monitor
  []
  (try
    (amqp/conn-monitor message-handler)
    (catch Exception e
      (log/error "[messaging-initialization]" (ce/format-exception e)))))

(defn messaging-initialization
  "Initializes the AMQP messaging handling, registering (message-handler) as the callback."
  []
  (if-not (cfg/rabbitmq-enabled)
    (log/warn "[messaging-initialization] iRODS messaging disabled"))
  
  (when (cfg/rabbitmq-enabled)
    (log/warn "[messaging-initialization] iRODS messaging enabled")
    (.start (Thread. receive))
    (monitor)))
