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
            [taoensso.timbre.appenders.core :as appenders]
            [clojure.string :as str]))

(timbre/refer-timbre)

(timbre/merge-config!
 {:level     :debug
  :appenders {:println {:enabled? false}}})   

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname "jira-reporter.log"})}})

(timbre/merge-config!
 {:appenders {:spit (appenders/spit-appender {:fname "jira-reporter.jira.log"
                                              :ns-whitelist ["jira-reporter.jira-api" "jira-reporter.rest-client"]})}})

(def- cli-options
  [[nil "--list-boards"         "List the names of the boards"]
   [nil "--list-sprints"        "List the names of the sprints"]
   [nil "--sprint-report"       "Generate a report for the sprint"]
   [nil "--daily-report"        "Generate a daily status report for the sprint"]
   [nil "--burndown"            "Generate a burndown for the sprint"]
   [nil "--project-report NAME" "Generate a project report for the project named NAME"]
   [nil "--sprint-name NAME"    "Use sprint named NAME"]
   [nil "--board-name NAME"     "Use board named NAME"]
   [nil "--jql QUERY"           "Use the specified JQL query"]
   [nil "--tsv"                 "Output the data as TSV for Excel"]
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

(defn- generate-exit-message [report-type requirements]
  (str "Option " (name report-type) " requires the following options to also be specified: " (->> requirements (map name) (string/join ", "))))

(defn- validate-options [options]
  (let [valid-options {:burndown      [:board-name :sprint-name]
                       :daily-report  [:board-name :sprint-name]
                       :sprint-report [:board-name :sprint-name]
                       :list-sprints  [:board-name]
                       :jql           nil}
        report-type   (cond
                        (:burndown options)      :burndown
                        (:daily-report options)  :daily-report
                        (:sprint-report options) :sprint-report
                        (:list-sprints options)  :list-sprints
                        (:jql options)           :jql)
        requirements  (get valid-options report-type)]
    (if-not (= requirements (keys (select-keys options requirements)))
      {:exit-message (generate-exit-message report-type requirements) :ok? false}
      {:options options :ok? true})))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)            {:exit-message (usage summary)    :ok? true}
      errors                     {:exit-message (error-msg errors) :ok? false}
      :else                      (validate-options options))))

(defn- display-table
  [report]
  (dorun
   (for [{:keys [title columns rows]} report]
     (do
       (println title)
       (if (empty? rows)
         (println "\nNone")
         (pprint/print-table columns rows))
       (println)))))

(defn- select-vals [m ks]
  (map (partial get m) ks))

(defn- display-tsv
  [report]
  (dorun
   (for [{:keys [title columns rows]} report]
     (do
       (println title)
       (if (empty? rows)
         (println "\nNone")
         (do
           (println)
           (println (string/join "\t" columns))
           (dorun (for [row rows] (println (string/join "\t" (select-vals row columns)))))
           (println)))))))

(defn display-report [options report]
  (info options)
  (if (:tsv options)
    (display-tsv report)
    (display-table report)))

(defn- execute-action [args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (println exit-message)
      (cond
        (:list-sprints options)   (display-report options (reports/generate-sprint-names-report options))
        (:daily-report options)   (display-report options (reports/generate-daily-report options))
        (:sprint-report options)  (display-report options (reports/generate-sprint-report options))
        (:burndown options)       (display-report options (reports/generate-burndown options))
        (:project-report options) (display-report options (reports/generate-project-report options))
        (:jql options)            (error "not implemented") ;; 
        :else                     (error "Unrecognised action"))
      )))

(defn -main [& args]
  (info "Starting application")
  (mount/start)
  (execute-action args)
  (mount/stop)
  (info "Done"))
