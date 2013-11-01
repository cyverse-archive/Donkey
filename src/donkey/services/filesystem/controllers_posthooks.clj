(ns donkey.services.filesystem.controllers-posthooks
  (:use [donkey.services.filesystem.controllers]
        [donkey.services.filesystem.common-paths])
  (:require [dire.core :refer [with-post-hook!]]
            [clojure.tools.logging :as log]))

(with-post-hook! #'do-copy (log-func "do-copy"))
(with-post-hook! #'do-paths-contain-space (log-func "do-paths-contain-space"))
(with-post-hook! #'do-replace-spaces (log-func "do-replace-spaces"))
(with-post-hook! #'do-read-chunk (log-func "do-read-chunk"))
(with-post-hook! #'do-overwrite-chunk (log-func "do-overwrite-chunk"))
(with-post-hook! #'do-get-csv-page (log-func "do-get-csv-page"))
(with-post-hook! #'do-read-csv-chunk (log-func "do-read-csv-chunk"))
