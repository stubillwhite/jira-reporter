(ns jira-reporter.analysis
  (:require [clojure.spec.alpha :as spec]
            [jira-reporter.date :as date]
            [jira-reporter.jira :as jira]
            [jira-reporter.schema.domain :as schema-domain]
            [jira-reporter.utils :refer [def-]]
            [taoensso.timbre :as timbre])
  (:import [java.time ZonedDateTime ZoneId]))

(timbre/refer-timbre)

(defn- add-current-state-if-open [history]
  (if (and (not-empty history) (not (contains? (jira/closed-states) (-> history last :to))))
    (concat history [(-> history last (assoc :date (date/current-date-time)))])
    history))

(defn- state-category [id state]
  (condp contains? state
    (jira/to-do-states)       :todo
    (jira/blocked-states)     :blocked
    (jira/in-progress-states) :in-progress
    (jira/deployment-states)  :deployment
    (jira/closed-states)      :closed
    (do
      (warn "Unknown JIRA state" state "for issue with ID" id)
      :other)))

(defn- status-change-only [history]
  (filter (fn [x] (= (:field x) "status")) history))

(defn- calculate-time-in-state [issue]
  (let [id       (:id issue)
        history  (-> (:history issue) add-current-state-if-open status-change-only)
        add-time (fn [a b] (fnil (partial + (date/working-hours-between (:date a) (:date b))) 0))]
    (reduce (fn [acc [a b]] (update acc (state-category id (:to a)) (add-time a b)))
            {}
            (partition 2 1 history))))

(defn- with-time-in-state [issue]
  (assoc issue :time-in-state
         (calculate-time-in-state issue)))

;; TODO: Not quite right; should just be for transition to a closed state
(defn calculate-lead-time-in-days [issue]
  (let [history (add-current-state-if-open (:history issue))]
    (when (seq history)
      (inc (date/working-days-between (-> history first :date) (-> history last :date))))))

(defn- with-lead-time-in-days [issue]
  (assoc issue :lead-time-in-days
         (calculate-lead-time-in-days issue)))

(defn add-derived-fields
  "Augment the specified issue with derived fields."  
  [issue]
  {:post [(spec/assert ::schema-domain/enriched-issue %)]}
  (-> issue
      (with-lead-time-in-days)
      (with-time-in-state)))
