(ns jira-reporter.issue-filters
  (:require [jira-reporter.date :as date]
            [jira-reporter.jira :as jira]
            [taoensso.timbre :as timbre]
            [clojure.set :as set]))

(timbre/refer-timbre)

(defn- status-is? [states issue]
  (contains? states (:status issue)))

(defn- type-is? [states issue]
  (contains? states (:type issue)))

(defn- before-or-equal? [a b]
  (or (= a b) (.isBefore a b)))

(defn- set-field-state-at-date [{:keys [history] :as issue} cutoff-date field]
  (let [filtered-history (filter (fn [x] (= field (:field x))) history)]
    (if (empty? filtered-history)
      issue
      (reduce
       (fn [acc {:keys [date field to]}] (assoc issue (keyword field) to) issue)
       (assoc issue (keyword field) (-> filtered-history first :from))
       (take-while (fn [x] (before-or-equal? (:date x) cutoff-date)) filtered-history)))))

(defn- field-value-at-date [issue field date history]
  (reduce
   (fn [value entry] (if (= field (:field entry)) (:to entry) value))
   (get issue (keyword field))
   history))

(defn- field-value-at-date [issue field date history]
  (let [sorted-history    (sort-by :date history)
        history-for-field (filter (fn [x] (= (:field x) field)) sorted-history)
        filtered-history  (filter (fn [x] (before-or-equal? (:date x) date)) history-for-field)
        initial-value     (or (get (first history-for-field) :from)
                              (get issue (keyword field)))]
    (reduce
     (fn [value entry] (:to entry))
     initial-value
     filtered-history)))

;; -----------------------------------------------------------------------------
;; Public functions
;; -----------------------------------------------------------------------------

;; TODO: Needs also to respect buddy, which needs to redirect through JIRA map
(defn issue-at-date [date issue]
  "Returns the the issue in the state is was on the specified date, or nil if the issue did not exist. Only certain
  fields will reflect the state on the date, namely status, type, and history."
  (when (before-or-equal? (:created issue) date)
    (let [pruned-history  (->> (:history issue)
                               (sort-by :date)
                               (take-while (fn [x] (before-or-equal? (:date x) date))))]
      (assoc issue
             :status  (field-value-at-date issue "status"  date (:history issue))
             :type    (field-value-at-date issue "type"    date (:history issue))
             ;; :history (field-value-at-date issue "history" date (:history issue))
             ;; :buddies (field-value-at-date issue "buddies" date (:history issue))
             :history pruned-history))))

(defn issues-at-date [date issues]
  "See issue-at-date. Issues which were created after the specified date will be removed."
  (->> issues
       (map (partial issue-at-date date))
       (filter identity)))

(defn to-do?
  "Returns true if the issue is in to-do, false otherwise."
  [issue]
  (status-is? (jira/to-do-states) issue))

(defn blocked?
  "Returns true if the issue is blocked, false otherwise."
  [issue]
  (status-is? (jira/blocked-states) issue))

(defn in-progress?
  "Returns true if the issue is in progress, false otherwise."
  [issue]
  (status-is? (jira/in-progress-states) issue))

(defn changed-state-in-the-last-day?
  "Returns true if the issue changed state in the previous day, false otherwise."
  [issue]
  (if-let [last-change (-> issue :history last :date)]
    (<= (date/working-days-between last-change (date/current-date)) 1)
    false))

(defn awaiting-deployment?
  "Returns true if the issue is awaiting deployment, false otherwise."
  [issue]
  (status-is? (jira/deployment-states) issue))

(defn epic?
  "Returns true if the issue is a epic, false otherwise."
  [issue]
  (type-is? (jira/epic-types) issue))

(defn story?
  "Returns true if the issue is a story, false otherwise."
  [issue]
  (type-is? (jira/story-types) issue))

(defn task?
  "Returns true if the issue is a task, false otherwise."
  [issue]
  (type-is? (jira/task-types) issue))

(defn subtask?
  "Returns true if the issue is a subtask, false otherwise."
  [issue]
  (type-is? (jira/subtask-types) issue))

(defn no-parent?
  "Returns true if the issue has no parent, false otherwise."
  [{:keys [parent-id epic]}]
  (or (nil? parent-id) (= parent-id epic)))

(defn no-subtasks?
  "Returns true if the issue has no subtasks, false otherwise."
  [issue]
  (empty? (:subtask-ids issue)))

(declare personal-development?)

(declare user-level-task?)

(declare bug?)

(defn business-deliverable?
  "Returns true if the issue is a self-contained deliverable, false otherwise."
  [issue]
  (let [{:keys [parent-id epic]} issue]
    (and (not (personal-development? issue))
         (or (story? issue)
             (and (task? issue) (no-subtasks? issue))
             (and (bug? issue) (no-parent? issue))))))

(defn user-level-task?
  "Returns true if the issue is a self-contained user task, false otherwise."
  [issue]
  (or (task? issue)
      (subtask? issue)
      (bug? issue)
      (and (story? issue) (no-subtasks? issue))))

(defn sized?
  "Returns true if the issue is sized, false otherwise."
  [issue]
  (not (nil? (:points issue))))

(declare breakage?)

(defn bug?
  "Returns true if the issue is a bug, false otherwise."
  [issue]
  (and (type-is? (jira/bug-types) issue)
       (not (breakage? issue))))

(defn gdpr?
  "Returns true if the issue is a GDPR issue, false otherwise."
  [issue]
  (type-is? (jira/gdpr-types) issue))

(defn to-do?
  "Returns true if the issue is in the to-do state, false otherwise."
  [issue]
  (status-is? (jira/to-do-states) issue))

(defn closed?
  "Returns true if the issue is closed, false otherwise."
  [issue]
  (status-is? (jira/closed-states) issue))

(defn open?
  "Returns true if the issue is not closed, false otherwise."
  [issue]
  (not (closed? issue)))

(defn assigned?
  "Returns true if the issue is assigned, false otherwise."
  [issue]
  (not (nil? (:assignee issue))))

(defn buddied?
  "Returns true if the issue has a buddy, false otherwise."
  [issue]
  (not (empty? (:buddies issue))))

(defn has-labels?
  "Returns true if the issue contains the specified labels, false otherwise."
  [labels issue]
  (let [label-set (into #{} labels)]
    (= label-set (clojure.set/intersection label-set (into #{} (:labels issue))))))

(defn needs-size?
  "Returns true if the issue needs size, false otherwise."
  [issue]
  (and (business-deliverable? issue) (not (sized? issue))))

(defn needs-triage?
  "Returns true if the issue needs triage, false otherwise."
  [issue]
  (and (bug? issue)
       (not (has-labels? ["recs_triaged"] issue))))

(defn breakage?
  "Returns true if the issue is a breakage, false otherwise."
  [issue]
  (and (type-is? (jira/bug-types) issue)
       (has-labels? ["recs_breakage"] issue)))

(defn data-science?
  "Returns true if the issue is a data science issue, false otherwise."
  [issue]
  (has-labels? ["recs_ds"] issue))

(defn engineering?
  "Returns true if the issue is an engineering issue, false otherwise."
  [issue]
  (has-labels? ["recs_eng"] issue))

(defn infrastructure?
  "Returns true if the issue is an infrastructure issue, false otherwise."
  [issue]
  (or (has-labels? ["recs_infra"] issue) 
      (has-labels? ["kd_infra"] issue)))

(defn support?
  "Returns true if the issue is a support issue, false otherwise."
  [issue]
  (has-labels? ["recs_support"] issue))

(defn personal-development?
  "Returns true if the issue is a personal development issue, false otherwise."
  [issue]
  (has-labels? ["recs_pd"] issue))

(defn miscellaneous?
  "Returns true if the issue is a miscellaneous project issue, false otherwise."
  [issue]
  (or (personal-development? issue)
      (gdpr? issue)))

(defn unallocated?
  "Returns true if the issue has not been allocated to a discipline, false otherwise."
  [issue]
  (not (or (data-science? issue)
           (engineering? issue)
           (infrastructure? issue)
           (support? issue)
           (miscellaneous? issue))))

(defn missing-team-assignment?
  "Returns true if the issue has not been assigned to a team, false otherwise."
  [issue]
  (and (business-deliverable? issue)
       (nil? (:team issue))))
