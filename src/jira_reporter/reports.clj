(ns jira-reporter.reports
  (:require [clojure.pprint :as pprint]
            [com.rpl.specter :refer [ALL collect END filterer MAP-VALS putval select select* selected? transform]]
            [jira-reporter.analysis :as analysis]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.issue-filters :as issue-filters]
            [jira-reporter.jira :as jira]
            [jira-reporter.utils :refer [def-]]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn report-issues-blocked [issues]
  (println "\nIssues blocked")
  (pprint/print-table [:id :title :assignee :lead-time-in-days]
                      (issue-filters/blocked issues)))

(defn report-issues-in-progress [issues]
  (println "\nIssues in progress")
  (pprint/print-table [:id :title :assignee :lead-time-in-days]
                      (issue-filters/in-progress issues)))

(defn report-issues-changed-state [issues]
  (println "\nIssues which changed state yesterday")
  (pprint/print-table [:id :status :title :assignee :lead-time-in-days]
                      (issue-filters/changed-state-in-the-last-day issues)))

(defn report-issues-awaiting-deployment [issues]
  (println "\nIssues awaiting deployment")
  (pprint/print-table [:id :status :title :assignee :lead-time-in-days]
                      (issue-filters/awaiting-deployment issues)))

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

(defn report-issues-summary [issues]
  (println "\nIssue summary")
  (pprint/print-table
     [{:category "Story" :open   (->> issues issue-filters/stories issue-filters/open   count)
                         :closed (->> issues issue-filters/stories issue-filters/closed count)}
      {:category "Task"  :open   (->> issues issue-filters/tasks   issue-filters/open   count)
                         :closed (->> issues issue-filters/tasks   issue-filters/closed count)}
      {:category "Bug"   :open   (->> issues issue-filters/bugs    issue-filters/open   count)
                         :closed (->> issues issue-filters/bugs    issue-filters/closed count)}
      {:category "GDPR"  :open   (->> issues issue-filters/gdpr    issue-filters/open   count)
                         :closed (->> issues issue-filters/gdpr    issue-filters/closed count)}
      {:category "Total" :open   (->> issues                       issue-filters/open   count)
                         :closed (->> issues                       issue-filters/closed count)}]))

(defn report-task-time-in-state [issues]
  (println "\nTask lead time in working days and working hours in state")
  (pprint/print-table [:id :title :lead-time-in-days :todo :in-progress :blocked :deployment :other]
                      (->> issues
                           (issue-filters/tasks)
                           (map #(merge % (:time-in-state %))))))  

;; (defn report-story-time-in-state [issues]
;;   (println "\nStory lead time in working days")
;;   (pprint/print-table [:id :title :lead-time-in-days]
;;                       (->> issues
;;                            (issue-filters/stories)
;;                            (map #(merge % (:time-in-state %))))))

(defn generate-sprint-report
  "Generate a report for a sprint."
  ([config name]
   (let [issues (map analysis/add-derived-fields (jira/get-issues-in-sprint-named config name))]
     (generate-sprint-report config name issues)))

  ([config name issues]
   (report-issues-summary issues)
   ;; (report-story-time-in-state issues)
   (report-task-time-in-state issues)))

;; Things to do next
;; - Issues opened and closed within a sprint
;; - Lead times per story using an aggregate-by-story function
