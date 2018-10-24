(ns jira-reporter.issue-filters-test
  (:require [jira-reporter.issue-filters :refer :all]
            [clojure.test :refer :all]
            [jira-reporter.date :as date])
  (:import [java.time DayOfWeek ZonedDateTime ZoneId]
           java.time.temporal.ChronoUnit))

(defn- days-offset [n]
  (.plus (date/today) n ChronoUnit/DAYS))

(defn- hours-offset [n]
  (.plus (date/today) n ChronoUnit/HOURS))

(defn- stub-issue [t-mod]
  {:history [{:date t-mod}]})

;; TODO: blocked, in-progress, awaiting-deployment, stories, tasks, bugs, dgpr, closed, open

(deftest changed-state-in-the-last-day-then-retains-issues-which-changed-state-within-the-last-working-day
  (let [stub-issue-1 (stub-issue (hours-offset -6))
        stub-issue-2 (stub-issue (hours-offset -12))
        stub-issue-3 (stub-issue (hours-offset -48))]
    (is (= (changed-state-in-the-last-day [stub-issue-1 stub-issue-2 stub-issue-3]) [stub-issue-1 stub-issue-2]))))
