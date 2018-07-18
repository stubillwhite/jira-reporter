(ns jira-reporter.app
  (:gen-class)
  (:require [clojure.tools.nrepl.server :refer [start-server stop-server]]
            [mount.core :as mount :refer [defstate]]
            [jira-reporter.config :refer [config]]
            [jira-reporter.core :as core]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn -main [& args]
  (info "Starting application")
  (mount/start)
  (core/generate-daily-report config)
  (mount/stop)
  (info "Done"))
