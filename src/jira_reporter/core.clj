(ns jira-reporter.core
  (:require [clojure.pprint :as pprint]
            [jira-reporter.api :as api]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]))

(timbre/refer-timbre)

(timbre/merge-config! {:appenders {:println {:enabled? false}}})   

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname "jira-reporter.log"})}})

(defn- in-progress? [issue]
  (= "In Progress" (:status issue)))

(defn- blocked? [issue]
  (= "Blocked"  (:status issue)))

(defn- awaiting-deployment? [issue]
  (contains? #{"Deploy to SIT" "Deploy to Prod"}  (:status issue)))

(defn- changed-state-yesterday? [issue]
  (when-let [last-change (-> issue :history last :date)]
    (>= 1 (date/workdays-between last-change (date/today)))))

(defn report-issues-in-progress [issues]
  (println "\nIssues in progress")
  (pprint/print-table [:id :title :assignee :days-in-progress]
                              (->> issues
                                   (filter in-progress?))))

(defn report-issues-blocked [issues]
  (println "\nIssues blocked")
  (pprint/print-table [:id :title :assignee :days-in-progress]
                      (->> issues
                           (filter blocked?))))

(defn report-issues-changed-state [issues]
  (println "\nIssues which changed state yesterday")
  (pprint/print-table [:id :status :title :assignee :days-in-progress]
                      (->> issues
                           (filter changed-state-yesterday?))))

(defn report-issues-awaiting-deployment [issues]
  (println "\nIssues awaiting deployment")
  (pprint/print-table [:id :status :title :assignee :days-in-progress]
                      (->> issues
                           (filter awaiting-deployment?))))

(defn generate-daily-report
  ([config]
   (generate-daily-report config (api/issues-in-curent-sprint config)))

  ([config issues]
   (report-issues-blocked issues)
   (report-issues-in-progress issues)
   (report-issues-changed-state issues)
   (report-issues-awaiting-deployment issues)))
