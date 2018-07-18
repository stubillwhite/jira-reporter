(ns jira-reporter.config
  (:require [clojure.edn :as edn]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn load-config
  "Returns the configuration file loaded from path."
  [path]
  (info "Loading configuration file" path)
  (-> path 
      slurp 
      edn/read-string))

(defstate config
  :start (load-config "resources/config.edn"))

