(ns jira-reporter.app
  (:gen-class)
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [jira-reporter.config :refer [config]]
            [jira-reporter.reports :as reports]
            [jira-reporter.utils :refer [def-]]
            [mount.core :as mount]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(def- cli-options
  [[nil "--sprint-name NAME" "Generate a report for the sprint named NAME"]
   [nil "--sprints" "List the names of the sprints"]
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
    (clojure.pprint/pprint options)
    (if exit-message
      (println exit-message)
      (cond
        (:sprints options)     (reports/generate-sprint-names-report config)
        (:sprint-name options) (reports/generate-sprint-report config (:sprint-name options))
        :else                  (reports/generate-daily-report config)))))

(defn -main [& args]
  (info "Starting application")
  (mount/start)
  (execute-action args config)
  (mount/stop)
  (info "Done"))

