(ns jira-reporter.app
  (:gen-class)
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [clojure.tools.nrepl.server :refer [start-server stop-server]]
            [jira-reporter.config :refer [config]]
            [jira-reporter.core :as core]
            [jira-reporter.utils :refer [def-]]
            [mount.core :as mount :refer [defstate]]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(def- cli-options
  [[nil "--sprint-name NAME" "Generate a report for the sprint named NAME"]
   ["-h" "--help"]])

(defn- usage [options-summary]
  (->> ["JIRA report generator."
        ""
        "Usage: jira-reporter [options]"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn- validate-args [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) {:exit-message (usage summary)    :ok? true}
      errors          {:exit-message (error-msg errors) :ok? false}
      :else           {:options options                 :ok? true})))

(defn- exit [status-code message]
  (println message)
  (System/exit status-code))

(defn- generate-report [args config]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (if-let [sprint-name (:sprint-name options)]
        (core/generate-sprint-report config sprint-name)
        (core/generate-daily-report config)))))

(defn -main [& args]
  (info "Starting application")
  (mount/start)
  (generate-report args config)
  (mount/stop)
  (info "Done"))
