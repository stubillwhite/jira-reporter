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

(defn- issues-in-sprint-named [board-name sprint-name]
  (map analysis/add-derived-fields (jira/get-issues-in-sprint-named board-name sprint-name)))

;; TODO: Should be on the 'add metadata bit'
(defn- add-time-in-state [t]
  (let [days-in-state (fn [x] (int (/ (get-in t [:time-in-state x] 0) 7)))]
    (-> t
        (assoc :time-in-todo       (days-in-state :todo))
        (assoc :time-in-progress   (days-in-state :in-progress))
        (assoc :time-in-blocked    (days-in-state :blocked))
        (assoc :time-in-deployment (days-in-state :deployment)))))

;; -----------------------------------------------------------------------------
;; Sprint names
;; -----------------------------------------------------------------------------

(defn generate-sprint-names-report
  "Generate a report of the sprint names for a board."
  [options]
  [{:title   "Sprint names"
    :columns [:name]
    :rows    (for [name (jira/get-sprint-names (:board-name options))] {:name name})}])

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
  ([options]
   (let [{:keys [board-name sprint-name]} options
         issues                           (issues-in-sprint-named board-name sprint-name)]
     (generate-daily-report options issues)))

  ([options issues]
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

;; TODO: Should be :open :raised :closed
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
   (let [{:keys [board-name sprint-name]} options
         sprint                           (jira/get-sprint-named board-name sprint-name)
         issues                           (->> (issues-in-sprint-named board-name sprint-name)
                                               (map (partial issues-at-date (:endDate sprint)))
                                               (filter (fn [x] (not (closed? (:status (issue-at-date (:startDate sprint) x)))))))]
     (generate-sprint-report options issues)))

  ([options issues]
   [(report-work-delivered issues)
    (report-issues-summary issues)]))

;; -----------------------------------------------------------------------------
;; Burndown
;; -----------------------------------------------------------------------------

(def- formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd E"))

(defn- format-date [date]
  (.format formatter date))

(defn- calculate-burndown-metrics [date issues]
  (let [count-of   (fn [& preds] (->> issues (filter (apply every-pred preds)) count))
        non-bug?   (complement bug?)
        sum-points (fn [xs] (->> xs (map :points) (filter identity) (reduce + 0.0)))]
    {:date        (format-date date)
     :open        (count-of non-bug? open?)
     :closed      (count-of non-bug? closed?)
     :total       (count-of non-bug?)
     :bugs-open   (count-of bug? open?)
     :bugs-closed (count-of bug? closed?)
     :points      (->> issues (filter closed?) (sum-points))}))

;; TODO: Move to filters or dates
(defn- before-or-equal? [a b]
  (let [date-a (date/truncate-to-days a)
        date-b (date/truncate-to-days b)]
    (or (= date-a date-b) (.isBefore date-a date-b))))

(defn- calculate-burndown-metrics-at-date [date issues]
  (calculate-burndown-metrics date (issues-at-date (.plus date 23 ChronoUnit/HOURS) issues)))

(defn- calculate-burndown [start-date end-date issues]
  (->> (date/timestream (date/truncate-to-days start-date) 1 ChronoUnit/DAYS)
       (take-while (fn [x] (and (before-or-equal? x (date/current-date)) (before-or-equal? x end-date))))
       (filter (fn [x] (date/working-day? x)))
       (map (fn [x] (calculate-burndown-metrics-at-date x issues)))))

(defn report-burndown [start-date end-date issues]
  {:title   "Burndown"
   :columns [:date :open :closed :total :points :bugs-open :bugs-closed]
   :rows    (calculate-burndown start-date end-date issues)})

(defn generate-burndown-report
  "Generate a burndown report."
  ([options]
   (let [{:keys [board-name sprint-name]} options
         sprint                           (jira/get-sprint-named board-name sprint-name)
         issues                           (jira/get-issues-in-sprint-named board-name sprint-name)]
     (generate-burndown-report options (:startDate sprint) (:endDate sprint) issues)))

  ([options start-date end-date issues]
   [(report-burndown start-date end-date issues)]))

;; -----------------------------------------------------------------------------
;; Backlog
;; -----------------------------------------------------------------------------

(defn report-sized-and-unsized-stories [issues]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))]
    {:title   "Sized and unsized open stories"
     :columns [:status :count]
     :rows    [{:status "Open and sized"   :count (count-of story? (complement closed?) sized?)}
               {:status "Open and unsized" :count (count-of story? (complement closed?) (complement sized?))}
               {:status "Open total"       :count (count-of story? (complement closed?))}]}))

(defn report-epics-in-progress [issues]
  (let [count-of         (fn [xs & preds] (->> xs (filter (apply every-pred preds)) count))
        epics-to-stories (group-by :epic issues)
        epic-metrics     (into {} (for [[k v] epics-to-stories] [k {:open   (count-of v (complement closed?))
                                                                    :closed (count-of v closed?)}]))]
    {:title   "Epics currently in progress"
     :columns [:id :title :open :closed]
     :rows    (->> issues
                   (filter (every-pred epic? in-progress?))
                   (map (fn [{:keys [id] :as issue}] (merge issue (get epic-metrics id)))))}))

(defn report-epics-open [issues]
  (let [count-of         (fn [xs & preds] (->> xs (filter (apply every-pred preds)) count))
        epics-to-stories (group-by :epic issues)
        epic-metrics     (into {} (for [[k v] epics-to-stories] [k {:open   (count-of v (complement closed?))
                                                                    :closed (count-of v closed?)}]))]
    {:title   "Epics not yet started"
     :columns [:id :title :open :closed]
     :rows    (->> issues
                   (filter (every-pred epic? to-do?))
                   (map (fn [{:keys [id] :as issue}] (merge issue (get epic-metrics id)))))}))

(defn report-epic-counts-by-state [issues]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))]
    {:title   "Epic counts by state"
     :columns [:status :count]
     :rows    [{:status "To-do"       :count (count-of epic? to-do?)}
               {:status "In progress" :count (count-of epic? in-progress?)}
               {:status "Closed"      :count (count-of epic? closed?)}]})) 

(defn to-age-in-days [issues]
  (map (fn [x] (.between java.time.temporal.ChronoUnit/DAYS (:created x) (date/current-date))) issues))

(defn- mean
  [& numbers]
    (if (empty? numbers)
      0
      (float (/ (reduce + numbers) (count numbers)))))

(defn report-story-age-metrics [issues]
  (let [issue-ages (->> issues (filter (every-pred story? to-do?)) to-age-in-days)]
    {:title   "Story ages in days"
     :columns [:metric :age-in-days]
     :rows    [{:metric "Oldest story" :age-in-days (apply max issue-ages)}
               {:metric "Newest story" :age-in-days (apply min issue-ages)}
               {:metric "Mean age"     :age-in-days (apply mean issue-ages)}]}))

(defn generate-backlog-report
  "Generate a backlog report."
  ([options]
   (let [{:keys [backlog-report]} options
         issues                 (jira/get-issues-in-project-named backlog-report)]
     (generate-backlog-report options issues)))

  ([options issues]
   [(report-story-age-metrics issues)
    (report-sized-and-unsized-stories issues)
    (report-epic-counts-by-state issues)
    (report-epics-open issues)
    (report-epics-in-progress issues)]))

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
