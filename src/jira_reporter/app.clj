(ns jira-reporter.app
  (:gen-class)
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
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
  [[nil "--list-boards"      "List the names of the boards"]
   [nil "--list-sprints"     "List the names of the sprints"]
   [nil "--sprint-report"    "Generate a report for the sprint"]
   [nil "--daily-report"     "Generate a daily status report for the sprint"]
   [nil "--burndown"         "Generate a burndown for the sprint"]
   [nil "--sprint-name NAME" "Use sprint named NAME instead of the current sprint"]
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

;; TODO: Validate option combinations
(defn- validate-args [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) {:exit-message (usage summary)    :ok? true}
      errors          {:exit-message (error-msg errors) :ok? false}
      :else           {:options options                 :ok? true})))

;; TODO: Remove explicit passing of config around

(defn display-report
  [report]
  (dorun
   (for [{:keys [title columns rows]} report]
     (do
       (println (str "\n" title))
       (pprint/print-table columns rows)))))

(defn- execute-action [args config]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (println exit-message)
      (cond
        (:list-boards options)   (display-report (reports/generate-board-names-report))
        (:list-sprints options)  (display-report (reports/generate-sprint-names-report))
        (:daily-report options)  (display-report (reports/generate-daily-report))
        (:sprint-report options) (display-report (reports/generate-sprint-report options))
        (:burndown options)      (display-report (reports/generate-burndown-report options))))))

(defn -main [& args]
  (info "Starting application")
  (mount/start)
  (execute-action args config)
  (mount/stop)
  (info "Done"))
