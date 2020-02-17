(ns jira-reporter.reports
  (:require [clojure.pprint :as pprint]
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

;; TODO: Move to filters or dates
(defn- before-or-equal? [a b]
  (let [date-a (date/truncate-to-days a)
        date-b (date/truncate-to-days b)]
    (or (= date-a date-b) (.isBefore date-a date-b))))

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
    :rows    (for [sprint (jira/get-sprints (:board-name options))] {:name (:name sprint)})}])

;; -----------------------------------------------------------------------------
;; Daily report
;; -----------------------------------------------------------------------------

(defn report-issues-blocked [issues]
  {:title   "Issues currently blocked"
   :columns [:id :type :title]
   :rows    (filter (every-pred blocked?) issues)})

(defn report-issues-started [issues]
  {:title   "Issues started yesterday"
   :columns [:id :type :title :assignee]
   :rows    (filter (every-pred changed-state-in-the-last-day? in-progress?) issues)})

(defn report-issues-in-progress [issues]
  {:title   "Issues still in progress"
   :columns [:id :type :title :assignee]
   :rows    (filter (every-pred (complement changed-state-in-the-last-day?) in-progress?) issues)})

(defn report-issues-ready-for-release [issues]
  {:title   "Issues awaiting release"
   :columns [:id :type :title :assignee]
   :rows    (filter (every-pred awaiting-deployment?) issues)})

(defn report-issues-closed [issues]
  {:title   "Issues closed yesterday"
   :columns [:id :type :title :assignee]
   :rows    (filter (every-pred changed-state-in-the-last-day? closed?) issues)})

(defn report-issues-needing-sizing [issues]
  {:title   "Issues needing sizing"
   :columns [:id :type :title :assignee]
   :rows    (filter needs-size? issues)})

(defn report-issues-needing-triage [issues]
  {:title   "Issues needing triage"
   :columns [:id :type :title :assignee]
   :rows    (filter needs-triage? issues)})

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
    (report-issues-closed issues)
    (report-issues-needing-sizing issues)
    (report-issues-needing-triage issues)])) 

;; -----------------------------------------------------------------------------
;; Sprint report
;; -----------------------------------------------------------------------------

(defn report-work-delivered [issues]
  {:title   "Stories and tasks delivered this sprint"
   :columns [:id :title :points :time-in-blocked :time-in-progress :time-in-deployment]
   :rows    (filter (every-pred deliverable? closed?) (map add-time-in-state issues))})

(defn- raised-in-sprint? [sprint issue]
  (and (before-or-equal? (:start-date sprint) (:created issue))
       (before-or-equal? (:created issue)     (:end-date sprint))))

(defn report-issues-summary [issues sprint]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))
        raised?  (fn [x] (raised-in-sprint? sprint x))]
    {:title   "Issue summary"
     :columns [:category :open :raised :closed]
     :rows [{:category "Story"   :open (count-of story?   open?) :raised (count-of story?   raised?) :closed (count-of story?   closed?)}
            {:category "Task"    :open (count-of task?    open?) :raised (count-of task?    raised?) :closed (count-of task?    closed?)}
            {:category "Subtask" :open (count-of subtask? open?) :raised (count-of subtask? raised?) :closed (count-of subtask? closed?)}
            {:category "Bug"     :open (count-of bug?     open?) :raised (count-of bug?     raised?) :closed (count-of bug?     closed?)}
            {:category "GDPR"    :open (count-of gdpr?    open?) :raised (count-of gdpr?    raised?) :closed (count-of gdpr?    closed?)}
            {:category "Total"   :open (count-of open?)          :raised (count-of raised?)          :closed (count-of closed?)}]}))

(defn- raised-in-sprint? [sprint issue]
  (and (before-or-equal? (:start-date sprint) (:created issue))
       (before-or-equal? (:created issue)     (:end-date sprint))))

(defn report-issues-raised-in-sprint [issues sprint]
  {:title   "Issues raised in the sprint"
   :columns [:id :type :title]
   :rows    (filter (partial raised-in-sprint? sprint) issues)})

(defn report-issues-closed-in-sprint [issues sprint]
  (let [add-raised-in-sprint (fn [x] (assoc x :raised-in-sprint? (raised-in-sprint? sprint x)))]
    {:title   "Issues closed in the sprint"
     :columns [:id :type :title :raised-in-sprint?]
     :rows    (->> issues
                   (filter (fn [x] (closed? x)))
                   (map add-raised-in-sprint))}))

(def white-dbg (atom nil))

(defn generate-sprint-report
  "Generate the sprint summary report."
  ([options]
   (let [{:keys [board-name sprint-name]} options
         sprint                           (jira/get-sprint-named board-name sprint-name)
         issues                           (->> (issues-in-sprint-named board-name sprint-name)
                                               ((partial issues-at-date (:end-date sprint)))
                                               (filter (fn [x] (not (closed? (:status (issue-at-date (:start-date sprint) x)))))))]
     (generate-sprint-report options issues sprint)))

  ([options issues sprint]
   (swap! white-dbg (fn [_] issues))
   [(report-work-delivered issues)
    (report-issues-summary issues sprint)
    (report-issues-raised-in-sprint issues sprint)
    (report-issues-closed-in-sprint issues sprint)]))

;; -----------------------------------------------------------------------------
;; Burndown
;; -----------------------------------------------------------------------------

(def- formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd E"))

(defn- format-date [date]
  (.format formatter date))

(defn- calculate-burndown-metrics [date issues]
  (let [count-of    (fn [& preds] (->> issues (filter (apply every-pred preds)) count))
        non-defect? (every-pred (complement bug?) (complement breakage?))
        sum-points  (fn [xs] (->> xs (map :points) (filter identity) (reduce + 0.0)))]
    {:date             (format-date date)
     :open             (count-of non-defect? open?)
     :closed           (count-of non-defect? closed?)
     :total            (count-of non-defect?)
     :bugs-open        (count-of bug? open?)
     :bugs-closed      (count-of bug? closed?)
     :breakages-open   (count-of breakage? open?)
     :breakages-closed (count-of breakage? closed?)
     :points           (->> issues (filter closed?) (sum-points))}))

(defn- calculate-burndown-metrics-at-date [date issues]
  (calculate-burndown-metrics date (issues-at-date (.plus date 23 ChronoUnit/HOURS) issues)))

(defn- calculate-burndown [start-date end-date issues]
  (->> (date/timestream (date/truncate-to-days start-date) 1 ChronoUnit/DAYS)
       (take-while (fn [x] (and (before-or-equal? x (date/current-date)) (before-or-equal? x end-date))))
       (filter (fn [x] (date/working-day? x)))
       (map (fn [x] (calculate-burndown-metrics-at-date x issues)))))

(defn report-burndown [start-date end-date issues]
  {:title   "Burndown"
   :columns [:date :open :closed :total :points :bugs-open :bugs-closed :breakages-open :breakages-closed]
   :rows    (calculate-burndown start-date end-date issues)})

(defn generate-burndown
  "Generate a burndown."
  ([options]
   (let [{:keys [board-name sprint-name]} options
         sprint                           (jira/get-sprint-named board-name sprint-name)
         issues                           (jira/get-issues-in-sprint-named board-name sprint-name)]
     (generate-burndown options sprint issues)))

  ([options sprint issues]
   [(report-burndown (:start-date sprint) (:end-date sprint) issues)]))

;; -----------------------------------------------------------------------------
;; Backlog
;; -----------------------------------------------------------------------------

(defn report-sized-and-unsized-deliverables [name issues]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))]
    {:title   (str name ": Sized and unsized open deliverables")
     :columns [:status :count]
     :rows    [{:status "Open and sized"   :count (count-of deliverable? (complement closed?) sized?)}
               {:status "Open and unsized" :count (count-of deliverable? (complement closed?) (complement sized?))}
               {:status "Open total"       :count (count-of deliverable? (complement closed?))}]}))

(defn- to-age-in-days [issues]
  (map (fn [x] (.between java.time.temporal.ChronoUnit/DAYS (:created x) (date/current-date))) issues))

(defn- mean
  [& numbers]
    (if (empty? numbers)
      0
      (float (/ (reduce + numbers) (count numbers)))))

(defn report-deliverable-age-metrics [name issues]
  (let [issue-ages (->> issues (filter deliverable?) to-age-in-days)]
    {:title   (str name ": Deliverable ages in days")
     :columns [:metric :age-in-days]
     :rows    [{:metric "Oldest" :age-in-days (if (empty? issue-ages) 0 (apply max issue-ages))}
               {:metric "Newest" :age-in-days (if (empty? issue-ages) 0 (apply min issue-ages))}
               {:metric "Mean"   :age-in-days (if (empty? issue-ages) 0 (apply mean issue-ages))}]}))

(defn- report-bucket-metrics [name issues]
  [(report-deliverable-age-metrics name issues)
   (report-sized-and-unsized-deliverables name issues)])

(defn- report-bucket-metrics [name issues]
  [(report-deliverable-age-metrics name issues)
   (report-sized-and-unsized-deliverables name issues)])

(defn- report-metrics-for-future-sprints [board-name]
  (let [get-metrics (fn [sprint-name]
                      (report-bucket-metrics sprint-name
                                             (jira/get-issues-in-sprint-named board-name sprint-name)))]
    (->> (jira/get-sprints board-name)
         (filter (fn [x] (= (:state x) "future")))
         (map :name)
         (sort)
         (mapcat get-metrics))))

(defn- report-metrics-for-backlog [board-name]
  (->> (jira/get-issues-in-backlog board-name)
       (report-bucket-metrics "Backlog")))

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

(defn generate-backlog-report
  "Generate a backlog report."
  [options]
  (let [{:keys [board-name project-name]} options]
    (concat
     (report-metrics-for-backlog board-name)
     (report-metrics-for-future-sprints board-name)
     (let [all-issues (jira/get-issues-in-project-named project-name)]
       [(report-epic-counts-by-state all-issues)
        (report-epics-open all-issues)
        (report-epics-in-progress all-issues)]))))

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

