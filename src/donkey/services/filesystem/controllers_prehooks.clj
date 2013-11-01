(ns donkey.services.filesystem.controllers-prehooks
  (:use [clojure-commons.error-codes]
        [donkey.services.filesystem.common-paths]
        [donkey.services.filesystem.validators]
        [donkey.util.validators]
        [donkey.util.config]
        [donkey.services.filesystem.controllers]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [dire.core :refer [with-pre-hook!]]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]))




