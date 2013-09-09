(ns donkey.clients.groupie
  (import
    (java.io StringReader)
    (java.security Security Signature)
    (javax.crypto Cipher)
    (org.apache.commons.codec.binary Base64)
    (org.bouncycastle.jce.provider BouncyCastleProvider)
    (org.bouncycastle.openssl PEMReader))
  (:use [clojure.java.io :only [reader]]
        [donkey.util.config]
        [donkey.util.transformers]
        [donkey.auth.user-attributes]
        [donkey.services.user-info :only [get-user-details]]
        [donkey.util.service]
        [ring.util.codec :only [url-encode]])
  (:require [donkey.util.config :as config]
            [cemerick.url :as curl]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- oauth-url
  [& components]
  (str (apply curl/url (config/oauth-base-url) components)))

;; hook up the BouncyCastleProvider which makes PEM things easier for us
(Security/addProvider (BouncyCastleProvider.))

;(defn read-key [key]
;  (-> key
;      StringReader.
;      PEMReader.
;      .readObject))

(defn get-keypair
  "Get a KeyPair from a PEM in a string"
  [pem]
  (let [sr (StringReader. pem) ;; have to create a StringReader
        pemreader (PEMReader. sr)] ;; and then create a PEMReader
    (.readObject pemreader))) ;; and then read that. point. java

(defn sign [data private-key]
  (let [sig (doto 
;              (Cipher/getInstance "SHA256withRSA")
;              (.init (Cipher/ENCRYPT_MODE) private-key)
              (Signature/getInstance "SHA256withRSA" "BC")
              (.initSign private-key)
              (.update data))]
    (.sign sig)))

(defn sign-obj
  "Signs a map as a url safe base64 encoded string"
  [ob private-key]
  (Base64/encodeBase64URLSafeString (sign (.getBytes (cheshire/encode ob) "UTF-8") private-key)))

(defn- base64-encode-segment
  ""
  [segment]
  (Base64/encodeBase64URLSafeString (.getBytes (cheshire/encode segment) "UTF-8")))

(defn- get-oath-payload
  ""
  [body]
  (string/join "."
               [(base64-encode-segment {:typ "JWT", :alg "sha256"})
                (base64-encode-segment body)]))

(defn get-groupie-auth
  ""
  [req]
  (let [now (quot (System/currentTimeMillis) 1000)
        payload (get-oath-payload {:iss "de", :scope "", :aud "", :exp now, :iat (+ now 3600)})
        signature (sign-obj payload (.getPrivate (get-keypair (config/oauth-pem))))
        assertion (string/join "." [payload signature])]
    (log/debug "payload\n" payload)
    (log/debug "signature\n" signature)
    (log/debug "assertion\n" assertion)
    (client/post (oauth-url)
                 {:form-params {:assertion assertion
                                :grant_type "urn:ietf:params:oauth:grant-type:jwt-bearer"}})
    )
;  (cheshire/encode {:algos (Security/getAlgorithms "Signature")})
  )
