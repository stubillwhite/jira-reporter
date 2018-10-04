(ns jira-reporter.reports
  (:require [clojure.pprint :as pprint]
            [com.rpl.specter :refer [ALL collect END filterer select select* selected? transform putval MAP-VALS]]
            [jira-reporter.analysis :as analysis]
            [jira-reporter.jira :as jira]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.utils :refer [def-]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]))

(timbre/refer-timbre)

;; TODO: Move this to somewhere core
(timbre/merge-config!
 {:appenders {:println {:enabled? false}}})   

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname "jira-reporter.log"})}})

(timbre/merge-config!
 {:appenders {:spit (appenders/spit-appender {:fname "jira-reporter.jira.log"})
              :ns-whitelist ["jira-reporter/jira-api"]}})

(defn- status-is? [states issue]
  (contains? states (:status issue)))

(defn- type-is? [states issue]
  (contains? states (:type issue)))

(defn report-issues-blocked [issues]
  (println "\nIssues blocked")
  (pprint/print-table [:id :title :assignee :lead-time-in-days]
                      (->> issues
                           (filter (partial status-is? jira/blocked-states)))))

(defn report-issues-in-progress [issues]
  (println "\nIssues in progress")
  (pprint/print-table [:id :title :assignee :lead-time-in-days]
                              (->> issues
                                   (filter (partial status-is? jira/in-progress-states)))))

(defn- changed-state-yesterday? [issue]
  (when-let [last-change (-> issue :history last :date)]
    (>= 1 (date/working-days-between last-change (date/today)))))

(defn report-issues-changed-state [issues]
  (println "\nIssues which changed state yesterday")
  (pprint/print-table [:id :status :title :assignee :lead-time-in-days]
                      (->> issues
                           (filter changed-state-yesterday?))))

(defn report-issues-awaiting-deployment [issues]
  (println "\nIssues awaiting deployment")
  (pprint/print-table [:id :status :title :assignee :lead-time-in-days]
                      (->> issues
                           (filter (partial status-is? jira/deployment-states)))))

(defn generate-daily-report
  "Generate the daily report for the current sprint."
  ([config]
   (let [issues (map analysis/add-derived-fields (jira/get-issues-in-current-sprint config))]
     (generate-daily-report config issues)))

  ([config issues]
   (report-issues-blocked issues)
   (report-issues-in-progress issues)
   (report-issues-changed-state issues)
   (report-issues-awaiting-deployment issues))) 

(defn generate-sprint-names-report
  "Generate a report of the sprint names for a board."
  ([config]
   (doseq [name (jira/get-sprint-names config)]
     (println name))))

;; TODO: Use comp
(defn report-issues-summary [issues]
 (println "\nIssue summary")
  (let [story?  (partial type-is? jira/story-types)
        task?   (partial type-is? jira/task-types)
        bug?    (partial type-is? jira/bug-types)
        gdpr?   (partial type-is? jira/gdpr-types)
        closed? (partial status-is? jira/closed-states)
        open?   (complement closed?)]
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
                         :closed (->> issues                 (filter closed?) (count))}])))

(defn report-time-in-state [issues]
  (println "\nLead time in working days and working hours in state")
  (pprint/print-table [:id :title :lead-time-in-days :todo :in-progress :blocked :deployment :other]
                      (->> issues
                           (filter (partial type-is? jira/task-types))
                           (map #(merge % (:time-in-state %))))))  


(defn generate-sprint-report
  "Generate a report for a sprint."
  ([config name]
   (let [issues (map analysis/add-derived-fields (jira/get-issues-in-sprint-named config name))]
     (generate-sprint-report config name issues)))

  ([config name issues]
   (report-issues-summary issues)
   (report-time-in-state issues)))
