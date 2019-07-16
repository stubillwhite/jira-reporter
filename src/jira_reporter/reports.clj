(ns jira-reporter.reports
  (:require [clojure.pprint :as pprint]
            [com.rpl.specter
             :refer
             [ALL
              collect
              END
              filterer
              MAP-VALS
              putval
              select
              select*
              selected?
              transform]]
            [jira-reporter.analysis :as analysis]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.issue-filters :refer :all]
            [jira-reporter.jira :as jira]
            [jira-reporter.utils :refer [def- map-vals]]
            [taoensso.timbre :as timbre])
  (:import java.time.format.DateTimeFormatter
           java.time.temporal.ChronoUnit))

(timbre/refer-timbre)

;; -----------------------------------------------------------------------------
;; Generic functions
;; -----------------------------------------------------------------------------

(defn- issues-in-current-sprint []
  (map analysis/add-derived-fields (jira/get-issues-in-current-sprint)))

(defn- issues-in-sprint-named [name]
  (map analysis/add-derived-fields (jira/get-issues-in-sprint-named name)))

;; TODO: Should be on the 'add metadata bit'
(defn- add-time-in-state [t]
  (let [days-in-state (fn [x] (int (/ (get-in t [:time-in-state x] 0) 7)))]
    (-> t
        (assoc :time-in-todo       (days-in-state :todo))
        (assoc :time-in-progress   (days-in-state :in-progress))
        (assoc :time-in-blocked    (days-in-state :blocked))
        (assoc :time-in-deployment (days-in-state :deployment)))))

;; -----------------------------------------------------------------------------
;; Board names
;; -----------------------------------------------------------------------------

(defn generate-board-names-report
  "Generate a report of the board names."
  []
  [{:title   "Board names"
    :columns [:name]
    :rows    (for [name (jira/get-board-names)] {:name name})}])

;; -----------------------------------------------------------------------------
;; Sprint names
;; -----------------------------------------------------------------------------

(defn generate-sprint-names-report
  "Generate a report of the sprint names for a board."
  []
  [{:title   "Sprint names"
    :columns [:name]
    :rows    (for [name (jira/get-sprint-names)] {:name name})}])

;; -----------------------------------------------------------------------------
;; Daily report
;; -----------------------------------------------------------------------------

(defn report-issues-blocked [issues]
  {:title   "Issues currently blocked"
   :columns [:id :title :parent-id :assignee :lead-time-in-days]
   :rows    (filter (every-pred blocked?) issues)})

(defn report-issues-started [issues]
  {:title   "Issues started yesterday"
   :columns [:id :title :parent-id :assignee]
   :rows    (filter (every-pred changed-state-in-the-last-day? in-progress?) issues)})

(defn report-issues-in-progress [issues]
  {:title   "Issues still in progress"
   :columns [:id :title :parent-id :assignee :lead-time-in-days]
   :rows    (filter (every-pred (complement changed-state-in-the-last-day?) in-progress?) issues)})

(defn report-issues-ready-for-release [issues]
  {:title   "Issues awaiting release"
   :columns [:id :status :title :parent-id :assignee :lead-time-in-days]
   :rows    (filter (every-pred awaiting-deployment?) issues)})

(defn report-issues-closed [issues]
  {:title   "Issues closed yesterday"
   :columns [:id :status :title :parent-id :assignee :lead-time-in-days]
   :rows    (filter (every-pred changed-state-in-the-last-day? closed?) issues)})

(defn generate-daily-report
  "Generate the daily report for the current sprint."
  ([]
   (generate-daily-report (issues-in-current-sprint)))

  ([issues]
   [(report-issues-blocked issues)
    (report-issues-started issues)
    (report-issues-in-progress issues)
    (report-issues-ready-for-release issues)
    (report-issues-closed issues)])) 

;; -----------------------------------------------------------------------------
;; Sprint report
;; -----------------------------------------------------------------------------

(defn report-work-delivered [issues]
  {:title   "Stories and tasks delivered this sprint"
   :columns [:id :title :points :time-in-blocked :time-in-progress :time-in-deployment]
   :rows    (filter (every-pred deliverable? closed?) (map add-time-in-state issues))})

(defn report-issues-summary [issues]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))]
    {:title   "Issue summary"
     :columns [:category :open :closed]
     :rows [{:category "Story" :open (count-of story? open?) :closed (count-of story? closed?)}
            {:category "Task"  :open (count-of task?  open?) :closed (count-of task?  closed?)}
            {:category "Bug"   :open (count-of bug?   open?) :closed (count-of bug?   closed?)}
            {:category "GDPR"  :open (count-of gdpr?  open?) :closed (count-of gdpr?  closed?)}
            {:category "Total" :open (count-of open?)        :closed (count-of closed?)}]}))

(defn generate-sprint-report
  "Generate the sprint summary report."
  ([options]
   (let [issues (if-let [sprint-name (:sprint-name options)]
                  (issues-in-sprint-named sprint-name)
                  (issues-in-current-sprint))]
     (generate-sprint-report options issues)))

  ([options issues]
   [(report-work-delivered issues)
    (report-issues-summary issues)]))

;; -----------------------------------------------------------------------------
;; Burndown
;; -----------------------------------------------------------------------------

(def- formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd"))

(defn- format-date [date]
  (.format formatter date))

(defn- tasks-open-and-closed [date issues]
  {:date   (format-date date)
   :open   (->> issues (filter open?) count)
   :closed (->> issues (filter (complement open?)) count)})

(defn- status-at-date [cutoff-date {:keys [history] :as issue}]
  (if (empty? (:history issue))
    issue
    (reduce
     (fn [acc {:keys [date field to]}] (if (= field "status") (assoc issue :status to) issue))
     (assoc issue :status (-> history first :from))
     (take-while (fn [x] (.isBefore (:date x) cutoff-date)) history))))

(defn- calculate-burndown [start-date end-date issues]
  (let [timestream (take-while (fn [x] (and (.isBefore x (date/today)) (.isBefore x end-date))) (date/timestream start-date 1 ChronoUnit/DAYS))]
    (->> timestream
         (map (fn [date] (tasks-open-and-closed date (map (partial status-at-date date) issues)))))))

(defn report-burndown [start-date end-date issues]
  {:title   (str "Burndown from " (format-date start-date) " to " (format-date end-date))
   :columns [:date :open :closed]
   :rows    (calculate-burndown start-date end-date issues)})

(defn generate-burndown-report
  "Generate a burndown report."
  ([options]
   (let [sprint (if-let [sprint-name (:sprint-name options)]
                  (jira/get-sprint-named sprint-name)
                  (jira/get-active-sprint))
         issues (jira/get-issues-in-sprint-named (:name sprint))]
     (generate-burndown-report options (:startDate sprint) (:endDate sprint) issues)))

  ([options start-date end-date issues]
   [(report-burndown start-date end-date issues)]))

;; -----------------------------------------------------------------------------
;; TODO: Sort all this out
;; -----------------------------------------------------------------------------

(defn- calculate-story-lead-time [story tasks]
  (analysis/calculate-lead-time-in-days
   (assoc story :history (->> (mapcat :history tasks)
                              (sort-by :date)))))

;; TODO: Wire in
;; TODO: Should be if history is empty
;; TODO: Merge story-closed-state with other states
(defn- story-state [config tasks]
  (let [all-tasks-closed? (every? closed? tasks)
        no-tasks-started? (every? to-do? tasks)]
    (cond
      (all-tasks-closed? (every? closed? tasks)) (get-in config [:schema :story-closed-state])
      (no-tasks-started? (every? closed? tasks)) (get-in config [:schema :story-to-do-state])
      :else (get-in config [:schema :story-in-progress-state]))))

(defn- build-story-history-from-tasks [config story tasks]
  (assoc story
         :history (->> (mapcat :history tasks)
                       (sort-by :date))
         :status (story-state config tasks)))

(defn- story-metrics [issues]
  (let [stories-by-id   (->> (filter story? issues) (group-by :id) (map-vals first))
        issues-by-story (group-by :parent-id issues)]
    (for [[k vs] (dissoc issues-by-story nil)]
      (assoc (stories-by-id k)
             :tasks-open      (->> vs (filter (every-pred task? open?))   count)
             :tasks-closed    (->> vs (filter (every-pred task? closed?)) count)
             :bugs-open       (->> vs (filter (every-pred bug? open?))    count)
             :bugs-closed     (->> vs (filter (every-pred bug? closed?))  count)
             :story-lead-time (calculate-story-lead-time (stories-by-id k) (issues-by-story k))))))

(defn report-story-metrics [issues]
  (println "\nStories and tasks in this sprint")
  (pprint/print-table [:id :title :status :points :tasks-open :tasks-closed :bugs-open :bugs-closed :lead-time-in-days :story-lead-time]
                      (story-metrics issues)))







(defn report-task-time-in-state [issues]
  (println "\nTask lead time in working days and working hours in state")
  (pprint/print-table [:id :title :lead-time-in-days :todo :in-progress :blocked :deployment :other]
                      (->> issues
                           ;; (filter non-story?)
                           (map #(merge % (:time-in-state %))))))  



(defn generate-project-report
  "Generate a report for a project."
  ([config name]
   (let [issues (map analysis/add-derived-fields (jira/get-issues-in-project-named name))]
     (generate-project-report config name issues)))

  ([config name issues]
   (report-story-metrics issues)))

;; Things to do next
;; - Issues opened and closed within a sprint
;; - Lead times per story using an aggregate-by-story function

;; Burndown
