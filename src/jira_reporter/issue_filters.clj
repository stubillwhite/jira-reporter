(ns jira-reporter.issue-filters
  (:require [jira-reporter.date :as date]
            [jira-reporter.jira :as jira]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn- status-is? [states issue]
  (contains? states (:status issue)))

(defn- type-is? [states issue]
  (contains? states (:type issue)))

(defn- before-or-equal? [a b]
  (or (= a b) (.isBefore a b)))

(defn- set-status-at-date [cutoff-date {:keys [history] :as issue}]
  (if (empty? history)
    issue
    (reduce
     (fn [acc {:keys [date field to]}] (if (= field "status") (assoc issue :status to) issue))
     (assoc issue :status (-> history first :from))
     (take-while (fn [x] (before-or-equal? (:date x) cutoff-date)) history))))

;; -----------------------------------------------------------------------------
;; Public functions
;; -----------------------------------------------------------------------------

(defn issue-at-date [date issue]
  "Returns the the issue in the state is was on the specified date, or nil if the issue did not exist. Only certain
  fields will reflect the state on the date, namely status."
  (clojure.pprint/pprint issue)
  (if (.isBefore (:created issue) date)
    nil
    (set-status-at-date date issue)))

(defn issues-at-date [date issues]
  "See issue-at-date. Issues which were created after the specified date will be removed."
  (->> issues
       (map (partial issue-at-date date))
       (filter identity)))

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

(defn story?
  "Returns true if the issue is a story, false otherwise."
  [issue]
  (type-is? (jira/story-types) issue))

(defn task?
  "Returns true if the issue is a task, false otherwise."
  [issue]
  (type-is? (jira/task-types) issue))

(defn no-parent?
  "Returns true if the issue has no parent, false otherwise."
  [issue]
  (nil? (:parent-id issue)))

(defn no-subtasks?
  "Returns true if the issue has no subtasks, false otherwise."
  [issue]
  (empty? (:subtask-ids issue)))

(defn deliverable?
  "Returns true if the issue is a self-contained deliverable, false otherwise."
  [issue]
  (or (no-parent? issue)))

(defn bug?
  "Returns true if the issue is a bug, false otherwise."
  [issue]
  (type-is? (jira/bug-types) issue))

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

