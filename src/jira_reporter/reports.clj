(ns jira-reporter.reports
  (:require [clojure.pprint :as pprint]
            [com.rpl.specter :refer [ALL collect END filterer MAP-VALS putval select select* selected? transform]]
            [jira-reporter.analysis :as analysis]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.issue-filters :as issue-filters :refer [task? bug? gdpr? story? open? closed? blocked? in-progress? changed-state-in-the-last-day? awaiting-deployment?]]
            [jira-reporter.jira :as jira]
            [jira-reporter.utils :refer [def-]]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(def- non-story? (complement story?))

(defn report-stories-closed [issues]
  (println "\nStories delivered this sprint")
  (pprint/print-table [:id :title :points]
                      (filter (every-pred story? closed?) issues)))

(defn- story-metrics [issues]
  (let [stories-by-id   (group-by :id (filter story? issues))
        issues-by-story (group-by :parent-id issues)]
    (for [[k vs] (dissoc issues-by-story nil)]
      (assoc (first (stories-by-id k))
             :tasks-open   (->> vs (filter (every-pred task? open?))   count)
             :tasks-closed (->> vs (filter (every-pred task? closed?)) count)
             :bugs-open    (->> vs (filter (every-pred bug? open?))    count)
             :bugs-closed  (->> vs (filter (every-pred bug? closed?))  count)))))

(defn report-story-metrics [issues]
  (println "\nStories in this sprint")
  (pprint/print-table [:id :title :status :points :tasks-open :tasks-closed :bugs-open :bugs-closed :lead-time-in-days]
                      (story-metrics issues)))

(defn report-issues-blocked [issues]
  (println "\nIssues currently blocked")
  (pprint/print-table [:id :title :parent-id :assignee :lead-time-in-days]
                      (filter (every-pred non-story? blocked?) issues)))

(defn report-issues-started [issues]
  (println "\nIssues started yesterday")
  (pprint/print-table [:id :title :parent-id :assignee]
                      (filter (every-pred non-story? changed-state-in-the-last-day? in-progress?) issues)))

(defn report-issues-in-progress [issues]
  (println "\nIssues currently in progress")
  (pprint/print-table [:id :title :parent-id :assignee :lead-time-in-days]
                      (filter (every-pred non-story? (complement changed-state-in-the-last-day?) in-progress?) issues)))

(defn report-issues-awaiting-deployment [issues]
  (println "\nIssues currently awaiting deployment")
  (pprint/print-table [:id :status :title :parent-id :assignee :lead-time-in-days]
                      (filter (every-pred non-story? awaiting-deployment?) issues)))

(defn report-issues-closed [issues]
  (println "\nIssues closed yesterday")
  (pprint/print-table [:id :title :parent-id :assignee :lead-time-in-days]
                      (filter (every-pred non-story? changed-state-in-the-last-day? closed?) issues)))

(defn generate-daily-report
  "Generate the daily report for the current sprint."
  ([config]
   (let [issues (map analysis/add-derived-fields (jira/get-issues-in-current-sprint config))]
     (generate-daily-report config issues)))

  ([config issues]
   (report-stories-closed issues)
   (report-story-metrics issues)
   (report-issues-blocked issues)
   (report-issues-started issues)
   (report-issues-in-progress issues)
   (report-issues-awaiting-deployment issues)
   (report-issues-closed issues))) 

(defn generate-board-names-report
  "Generate a report of the board names."
  ([config]
   (doseq [name (jira/get-board-names config)]
     (println name))))

(defn generate-sprint-names-report
  "Generate a report of the sprint names for a board."
  ([config]
   (doseq [name (jira/get-sprint-names config)]
     (println name))))

(defn report-issues-summary [issues]
  (println "\nIssue summary")
  (pprint/print-table
   [{:category "Story" :open   (->> issues (filter (every-pred story? open?))   count)
                       :closed (->> issues (filter (every-pred story? closed?)) count)}
    {:category "Task"  :open   (->> issues (filter (every-pred task?  open?))   count)
                       :closed (->> issues (filter (every-pred task?  closed?)) count)}
    {:category "Bug"   :open   (->> issues (filter (every-pred bug?   open?))   count)
                       :closed (->> issues (filter (every-pred bug?   closed?)) count)}
    {:category "GDPR"  :open   (->> issues (filter (every-pred gdpr?  open?))   count)
                       :closed (->> issues (filter (every-pred gdpr?  closed?)) count)}
    {:category "Total" :open   (->> issues (filter open?) count)
                       :closed (->> issues (filter closed?) count)}]))

(defn report-task-time-in-state [issues]
  (println "\nTask lead time in working days and working hours in state")
  (pprint/print-table [:id :title :lead-time-in-days :todo :in-progress :blocked :deployment :other]
                      (->> issues
                           ;; (filter non-story?)
                           (map #(merge % (:time-in-state %))))))  

(defn generate-sprint-report
  "Generate a report for a sprint."
  ([config name]
   (let [issues (map analysis/add-derived-fields (jira/get-issues-in-sprint-named config name))]
     (generate-sprint-report config name issues)))

  ([config name issues]
   (report-issues-summary issues)   
   ;; (report-story-time-in-state issues)
   ;; (report-task-time-in-state issues)
   ))

;; Things to do next
;; - Issues opened and closed within a sprint
;; - Lead times per story using an aggregate-by-story function

;; (generate-daily-report config)

