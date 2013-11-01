(ns donkey.services.filesystem.controllers-posthooks
  (:use [donkey.services.filesystem.controllers]
        [donkey.services.filesystem.common-paths])
  (:require [dire.core :refer [with-post-hook!]]
            [clojure.tools.logging :as log]))

