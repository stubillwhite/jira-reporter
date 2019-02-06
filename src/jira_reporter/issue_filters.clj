(ns jira-reporter.issue-filters
  (:require [jira-reporter.date :as date]
            [jira-reporter.jira :as jira]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn- status-is? [states issue]
  (contains? states (:status issue)))

(defn- type-is? [states issue]
  (contains? states (:type issue)))

;; Public functions

(def blocked?
  "Returns true if the issue is blocked, false otherwise."
  (partial status-is? (jira/blocked-states)))

(def in-progress?
  "Returns true if the issue is in progress, false otherwise."
  (partial status-is? (jira/in-progress-states)))

(defn changed-state-in-the-last-day?
  "Returns true if the issue changed state in the previous day, false otherwise."
  [issue]
  (if-let [last-change (-> issue :history last :date)]
    (<= (date/working-days-between last-change (date/today)) 1)
    false))

(def awaiting-deployment?
  "Returns true if the issue is awaiting deployment, false otherwise."
  (partial status-is? (jira/deployment-states)))

(def story?
  "Returns true if the issue is a story, false otherwise."
  (partial type-is? (jira/story-types)))

(def task?
  "Returns true if the issue is a task, false otherwise."
  (partial type-is? (jira/task-types)))

(def bug?
  "Returns true if the issue is a bug, false otherwise."
  (partial type-is? (jira/bug-types)))

(def gdpr?
  "Returns true if the issue is a GDPR issue, false otherwise."
  (partial type-is? (jira/gdpr-types)))

(def closed?
  "Returns true if the issue is closed, false otherwise."
  (partial status-is? (jira/closed-states)))

(def open?
  "Returns true if the issue is not closed, false otherwise."
  (complement closed?))

