(ns jira-reporter.issue-filters
  (:require [jira-reporter.date :as date]
            [jira-reporter.jira :as jira]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn- status-is? [states issue]
  (contains? states (:status issue)))

(defn- type-is? [states issue]
  (contains? states (:type issue)))

(defn- changed-state-in-the-last-day? [issue]
  (when-let [last-change (-> issue :history last :date)]
    (= 1 (date/working-days-between last-change (date/today)))))

;; Public functions

(defn blocked
  "Returns issues which are blocked."
  [issues]
  (filter (partial status-is? jira/blocked-states) issues))

(defn in-progress
  "Returns issues which are in progress."
  [issues]
  (filter (partial status-is? jira/in-progress-states) issues))

(defn changed-state-in-the-last-day
  "Returns issues which changed state in the last day."
  [issues]
  (filter changed-state-in-the-last-day? issues))

(defn awaiting-deployment
  "Returns issues which are awaiting deployment"
  [issues]
  (filter (partial status-is? jira/deployment-states) issues))

(defn stories
  "Returns issues which are stories."
  [issues]
  (filter (partial type-is? jira/story-types) issues))

(defn tasks
  "Returns issues which are tasks."
  [issues]
  (filter (partial type-is? jira/task-types) issues))

(defn bugs
  "Returns issues which are bugs."
  [issues]
  (filter (partial type-is? jira/bug-types) issues))

(defn gdpr
  "Returns issues which are GDPR tasks."
  [issues]
  (filter (partial type-is? jira/gdpr-types) issues))

(defn closed
  "Returns issues which are closed."
  [issues]
  (filter (partial status-is? jira/closed-states) issues))

(defn open
  "Returns issues which are open."
  [issues]
  (filter (complement (partial status-is? jira/closed-states)) issues))

(defn aggregate-by-story
  "TODO"
  [issues]
  )
