(ns jira-reporter.app
  (:gen-class)
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [jira-reporter.config :refer [config]]
            [jira-reporter.reports :as reports]
            [jira-reporter.utils :refer [def-]]
            [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]))

(timbre/refer-timbre)

(timbre/merge-config!
 {:level     :debug
  :appenders {:println {:enabled? false}}})   

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname "jira-reporter.log"})}})

(timbre/merge-config!
 {:appenders {:spit (appenders/spit-appender {:fname "jira-reporter.jira.log"})
              :ns-whitelist ["jira-reporter.jira-api" "jira-reporter.rest-client"]}})

(def- cli-options
  [[nil "--project-id ID"    "Select project identified by ID"]
   [nil "--sprint-name NAME" "Generate a report for the sprint named NAME"]
   [nil "--boards"           "List the names of the boards"]
   [nil "--sprints"          "List the names of the sprints"]
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

(defn- execute-action [args config]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (println exit-message)
      (cond
        (:boards options)      (reports/generate-board-names-report config)
        (:sprints options)     (reports/generate-sprint-names-report config)
        (:sprint-name options) (reports/generate-sprint-report config options)
        :else                  (reports/generate-daily-report config options)))))

(defn -main [& args]
  (info "Starting application")
  (mount/start)
  (execute-action args config)
  (mount/stop)
  (info "Done"))

