(ns jira-reporter.reports-test
  (:require [clojure.test :refer :all]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.reports :refer :all]
            [jira-reporter.test-common :refer :all]
            [jira-reporter.utils :refer [def-]]))

(def- two-days-ago "2000-02-01")
(def- yesterday    "2000-02-02")
(def- today        "2000-02-03")

(def- untouched-issue            (issue "1" "to-do"))
(def- existing-in-progress-issue (issue "2" "in-progress" :history [(status-change two-days-ago "in-progress")]))
(def- newly-in-progress-issue    (issue "3" "in-progress" :history [(status-change yesterday    "in-progress")]))
(def- newly-closed-issue         (issue "4" "closed"      :history [(status-change yesterday    "closed")]))
(def- deploy-issue               (issue "5" "deploy"))

(def- all-issues
  [untouched-issue
   existing-in-progress-issue
   newly-in-progress-issue
   newly-closed-issue
   deploy-issue])

(defn- test-tod1ay []
  (parse-date today))

(defn- containing-issues? [expected actual]
  (let [actual-rows (map (fn [x] (select-keys x (keys (first expected)))) (:rows actual))]
    (= expected actual-rows)))

(deftest report-work-delivered-then-reports-stories-and-orphan-tasks-delivered
  (with-redefs [date/today (fn [] (parse-date "2000-02-03"))
                config     test-config]
    (let [task-1  (task  "1" "to-do")
          task-2  (task  "2" "closed" :points 3)
          story-3 (story "3" "closed" :points 5)]
      (is (containing-issues? [task-2 story-3] (report-work-delivered [task-1 task-2 story-3]))))))

(deftest report-issues-started-then-reports-issues-moved-to-in-progress-in-the-previous-day
  (with-redefs [date/today test-today
                config     test-config]
    (is (containing-issues? [newly-in-progress-issue] (report-issues-started all-issues)))))

(deftest report-issues-in-progress-then-reports-issues-in-progress-which-did-not-start-in-the-previous-day
  (with-redefs [date/today test-today
                config     test-config]
    (is (containing-issues? [existing-in-progress-issue] (report-issues-in-progress all-issues)))))

(deftest report-issues-ready-for-release-then-reports-issues-ready-to-deploy
  (with-redefs [date/today test-today
                config     test-config]
    (is (containing-issues? [deploy-issue] (report-issues-ready-for-release all-issues)))))
