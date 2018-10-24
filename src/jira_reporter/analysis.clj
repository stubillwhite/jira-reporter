(ns jira-reporter.analysis
  (:require [jira-reporter.jira :as jira]
            [jira-reporter.date :as date]
            [jira-reporter.utils :refer [def-]]
            [taoensso.timbre :as timbre])
  (:import [java.time ZonedDateTime ZoneId]))

(timbre/refer-timbre)

(defn- add-current-state-if-open [history]
  (if (and (not-empty history) (not (contains? jira/closed-states (-> history last :to))))
    (concat history [(-> history last (assoc :date (date/now)))])
    history))

(defn- state-category [status]
  (condp contains? status
    jira/to-do-states       :todo
    jira/blocked-states     :blocked
    jira/in-progress-states :in-progress
    jira/deployment-states  :deployment
    jira/closed-states      :closed
    (do
      (warn "Unknown JIRA status" status)
      :other)))

(defn- calculate-time-in-state [issue]
  (let [history  (add-current-state-if-open (:history issue))
        add-time (fn [a b] (fnil (partial + (date/working-hours-between (:date a) (:date b))) 0))]
    (reduce (fn [acc [a b]] (update acc (state-category (:to a)) (add-time a b)))
            {}
            (partition 2 1 history))))

(defn- with-time-in-state [issue]
  (assoc issue :time-in-state
         (calculate-time-in-state issue)))

(defn- calculate-lead-time-in-days [issue]
  (let [history (add-current-state-if-open (:history issue))]
    (when (seq history)
      (inc (date/working-days-between (-> history first :date) (-> history last :date))))))

(defn- with-lead-time-in-days [issue]
  (assoc issue :lead-time-in-days
         (calculate-lead-time-in-days issue)))

(defn add-derived-fields [issue]
  "Augment the specified issue with derived fields."
  (-> issue
      (with-lead-time-in-days)
      (with-time-in-state)))
