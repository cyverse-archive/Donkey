(ns donkey.services.metadata.common-apps)

(defn agave-job-id?
  [id]
  (re-matches #"\d+" id))
