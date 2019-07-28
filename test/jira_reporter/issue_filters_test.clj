(ns jira-reporter.issue-filters-test
  (:require [jira-reporter.issue-filters :refer :all]
            [clojure.test :refer :all]
            [jira-reporter.date :as date])
  (:import [java.time DayOfWeek ZonedDateTime ZoneId]
           java.time.temporal.ChronoUnit))

(defn- hours-offset [n]
  (.plus (date/current-date) n ChronoUnit/HOURS))

(defn- stub-issue [t-mod]
  {:history [{:date t-mod}]})

;; TODO: blocked, in-progress, awaiting-deployment, stories, tasks, bugs, dgpr, closed, open

(deftest changed-state-in-the-last-day?-then-true-if-changed-state-within-the-last-working-day
  (is (= true  (changed-state-in-the-last-day? (stub-issue (hours-offset -6)))))
  (is (= true  (changed-state-in-the-last-day? (stub-issue (hours-offset -12)))))
  (is (= false (changed-state-in-the-last-day? (stub-issue (hours-offset -48))))))
