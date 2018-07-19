(ns jira-reporter.core
  (:require [clojure.pprint :as pprint]
            [jira-reporter.api :as api]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [jira-reporter.utils :refer [def-]]))

(timbre/refer-timbre)

(timbre/merge-config! {:appenders {:println {:enabled? false}}})   

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname "jira-reporter.log"})}})

(defn- in-progress? [issue]
  (= "In Progress" (:status issue)))

(defn- blocked? [issue]
  (= "Blocked"  (:status issue)))

(defn- closed? [issue]
  (= "Closed - DONE"  (:status issue)))

(def- open? (complement closed?))

(defn- story? [issue]
  (= "Story"  (:type issue)))

(defn- task? [issue]
  (contains? #{"Task" "Sub-task"}  (:type issue)))

(defn- bug? [issue]
  (contains? #{"Bug" "Bug Sub-task"}  (:type issue)))

(defn- gdpr? [issue]
  (= "GDPR Compliance"  (:type issue)))

(defn- awaiting-deployment? [issue]
  (contains? #{"Deploy to SIT" "Deploy to Prod"}  (:status issue)))

(defn- changed-state-yesterday? [issue]
  (when-let [last-change (-> issue :history last :date)]
    (>= 1 (date/workdays-between last-change (date/today)))))

(defn report-issues-summary [issues]
  (println "\nIssue summary")
  (pprint/print-table
   [{:category "Story" :open   (->> issues (filter story?) (filter open?)   (count))
                       :closed (->> issues (filter story?) (filter closed?) (count))}
    {:category "Task"  :open   (->> issues (filter task?)  (filter open?)   (count)) 
                       :closed (->> issues (filter task?)  (filter closed?) (count))}
    {:category "Bug"   :open   (->> issues (filter bug?)   (filter open?)   (count)) 
                       :closed (->> issues (filter bug?)   (filter closed?) (count))}
    {:category "GDPR"  :open   (->> issues (filter gdpr?)  (filter open?)   (count)) 
                       :closed (->> issues (filter gdpr?)  (filter closed?) (count))}
    {:category "Total" :open   (->> issues                 (filter open?)   (count)) 
                       :closed (->> issues                 (filter closed?) (count))}]))

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
   (report-issues-summary issues)
   (report-issues-blocked issues)
   (report-issues-in-progress issues)
   (report-issues-changed-state issues)
   (report-issues-awaiting-deployment issues)))
