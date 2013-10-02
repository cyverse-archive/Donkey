(ns donkey.clients.amqp
  (:require [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.exchange  :as le]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]
            [donkey.util.config    :as cfg]
            [clojure.tools.logging :as log]))

(defn test-msg-fn
  [channel {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (println (format "[donkey] Message: %s\tDelivery: %s\tContent Type: %s\tType: %s"
          (String. payload "UTF-8") delivery-tag content-type type)))

(defn connection
  "Creates and returns a new connection to the AMQP server."
  [conn-map]
  (rmq/connect conn-map))

(defn channel
  "Creates a channel on connection and returnes it."
  [connection]
  (lch/open connection))

(defn declare-exchange
  [channel exchange type & {:keys [durable auto-delete]
                            :or {durable false
                                 auto-delete false}}]
  (le/declare channel exchange type :durable durable :auto-delete auto-delete)
  channel)

(defn declare-queue
  [channel queue & {:keys [exclusive auto-delete] 
                    :or {exclusive false 
                         auto-delete false}}]
  (lq/declare channel queue :exclusive exclusive :auto-delete auto-delete)
  channel)

(defn bind
  [channel queue exchange]
  (lq/bind channel queue exchange)
  channel)

(defn publish
  [channel exchange queue message]
  (lb/publish channel exchange queue message))

(defn subscribe
  [channel queue msg-fn & {:keys [auto-ack]
                           :or {auto-ack true}}]
  (lc/subscribe channel queue msg-fn :auto-ack true)
  channel)

(def amqp-conn (ref nil))
(def amqp-channel (ref nil))

(defn connection-map
  []
  {:host     (cfg/rabbitmq-host)
   :port     (cfg/rabbitmq-port)
   :username (cfg/rabbitmq-user)
   :password (cfg/rabbitmq-pass)})

(defn connection-okay?
  [conn]
  (and (not (nil? conn))
       (rmq/open? conn)))

(defn channel-okay?
  [chan]
  (and (not (nil? chan))
       (rmq/open? chan)))

(defn setup-connection
  "Sets the amqp-conn ref if necessary and returns it. Must be called within (dosync)."
  []
  (if (connection-okay? @amqp-conn)
    @amqp-conn
    (ref-set amqp-conn (connection (connection-map)))))

(defn setup-channel
  "Sets the amqp-channel ref if necessary and returns it. Must be called within (dosync)."
  [conn]
  (if (channel-okay? @amqp-channel)
    @amqp-channel
    (ref-set amqp-channel (channel conn))))

(defn get-channel
  [msg-fn]
  (dosync
    (let [conn (setup-connection)
          chan (setup-channel conn)]
      (-> @amqp-channel
        (declare-exchange 
          (cfg/rabbitmq-exchange) 
          (cfg/rabbitmq-exchange-type)
          :durable (cfg/rabbitmq-exchange-durable?)
          :auto-delete (cfg/rabbitmq-exchange-auto-delete?))
        (declare-queue
          (cfg/rabbitmq-queue)
          :exclusive (cfg/rabbitmq-queue-exclusive?)
          :auto-delete (cfg/rabbitmq-queue-auto-delete?))
        (bind (cfg/rabbitmq-queue) (cfg/rabbitmq-exchange))
        (subscribe
          (cfg/rabbitmq-queue)
          msg-fn
          :auto-ack (cfg/rabbitmq-msg-auto-ack?))))))

(defn conn-monitor
  "Starts an infinite loop that checks the health of the amqp-conn and reconnects if necessary."
  []
  (.start 
    (Thread. 
      (fn [] 
        (loop []
          (dosync (setup-connection))
          (Thread/sleep (cfg/rabbitmq-health-check-interval))
          (recur))))))
