(ns donkey.util.messaging
  (:require [langohr.core       :as rmq]
            [langohr.channel    :as lch]
            [langohr.exchange   :as le]
            [langohr.consumers  :as lc]
            [langohr.queue      :as lq]
            [donkey.util.config :as cfg]))

(def rmq-conn (atom nil))
(def dataobj-ch (atom nil))

(defn connect
  "Connects to RabbitMQ, resets @rmq-conn and returns it."
  []
  (if (nil? @rmq-conn)
    (let [conn-map {:host     (cfg/rabbitmq-host)
                    :port     (cfg/rabbitmq-port)
                    :username (cfg/rabbitmq-user)
                    :password (cfg/rabbitmq-pass)}] 
    (reset! rmq-conn (rmq/connect conn-map)))
    @rmq-conn))

(defn dataobj-channel
  "Returns a channel object for the dataobj channels."
  []
  (if (nil? @dataobj-ch) 
    (reset! dataobj-ch (lch/open @rmq-conn))
    @dataobj-ch))

(defn dataobj-exchange
  []
  (le/declare @dataobj-ch (cfg/rabbitmq-exchange) "topic"))

(defn dataobj-message-handler
  "A handler for dataobj messages"
  [ch {:keys [content-type delivery-tag type] :as meta} payload]
  (println (format "[consumer] Msg: %s, Tag: %d, Content Type: %s, Type: %s"
                   (String. payload "UTF-8") delivery-tag content-type type)))

(defn setup
  []
  (connect)
  (dataobj-channel)
  (dataobj-exchange))

(defn dataobj-consumer
  [consumer-id]
  (let [queue-name (format "dataobj.queues.%s" consumer-id)]
    (lq/declare (dataobj-channel) queue-name :exclusive false :auto-delete true)
    (lq/bind (dataobj-channel) queue-name (cfg/rabbitmq-exchange) :routing-key (cfg/rabbitmq-dataobject-topic))
    (lc/subscribe (dataobj-channel) queue-name dataobj-message-handler :auto-ack true)))

