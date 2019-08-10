(ns jira-reporter.issue-filters-test
  (:require [clojure.test :refer :all]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.issue-filters :as issue-filters]
            [jira-reporter.test-common :refer [test-config story status-change]]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time DayOfWeek ZonedDateTime ZoneId]
           java.time.temporal.ChronoUnit))

(def- today        "2000-01-07Z")
(def- one-day-ago  "2000-01-06Z")
(def- two-days-ago "2000-01-05Z")

(defn- hours-offset [n]
  (.plus (date/parse-date today) n ChronoUnit/HOURS))

(defn- issue-modified-at [t-mod]
  {:history [{:date t-mod}]})

(deftest issue-at-date
  (testing "given created after date then nil"
    (is (nil? (issue-filters/issue-at-date (date/parse-date one-day-ago) {:created (date/parse-date today)}))))
  (testing "given status changed then can restore previous history"
    (let [issue    (story "1" "closed" :created (date/parse-date two-days-ago) :history [(status-change one-day-ago "in-progress") (status-change today "closed")])
          expected (-> issue
                       (assoc :status "in-progress")
                       (assoc :history [(status-change one-day-ago "in-progress")]))]
      (is (= expected (issue-filters/issue-at-date (date/parse-date one-day-ago) issue))))))

;; TODO issues-at-date

(deftest basic-predicates
  (with-redefs [config test-config]
    (testing "blocked?"
      (is (= true (issue-filters/blocked? {:status "blocked"})))
      (is (= false (issue-filters/blocked? {:status "not blocked"}))))
    (testing "in-progress?"
      (is (= true (issue-filters/in-progress? {:status "in-progress"})))
      (is (= false (issue-filters/in-progress? {:status "not in-progress"}))))
    (testing "awaiting-deployment?"
      (is (= true (issue-filters/awaiting-deployment? {:status "deploy"})))
      (is (= false (issue-filters/awaiting-deployment? {:status "not deploy"}))))
    (testing "story?"
      (is (= true (issue-filters/story? {:type "story"})))
      (is (= false (issue-filters/story? {:type "not story"}))))
    (testing "task?"
      (is (= true (issue-filters/task? {:type "task"})))
      (is (= false (issue-filters/task? {:type "not task"}))))
    (testing "no-parent?"
      (is (= true (issue-filters/no-parent? {})))
      (is (= false (issue-filters/no-parent? {:parent-id "parent"}))))
    (testing "no-subtasks?"
      (is (= true (issue-filters/no-subtasks? {})))
      (is (= false (issue-filters/no-subtasks? {:subtask-ids ["subtask"]}))))
    (testing "deliverable?"
      (is (= true (issue-filters/deliverable? {})))
      (is (= false (issue-filters/deliverable? {:parent-id "parent"}))))
    (testing "bug?"
      (is (= true (issue-filters/bug? {:type "bug"})))
      (is (= false (issue-filters/bug? {:type "not bug"}))))
    (testing "gdpr?"
      (is (= true (issue-filters/gdpr? {:type "gdpr"})))
      (is (= false (issue-filters/gdpr? {:type "not gdpr"}))))
    (testing "to-do?"
      (is (= true (issue-filters/to-do? {:status "to-do"})))
      (is (= false (issue-filters/to-do? {:status "not to-do"}))))
    (testing "closed?"
      (is (= true (issue-filters/closed? {:status "closed"})))
      (is (= false (issue-filters/closed? {:status "not closed"}))))
    (testing "open?"
      (is (= true (issue-filters/open? {:status "not closed"})))
      (is (= false (issue-filters/open? {:status "closed"}))))))

(deftest changed-state-in-the-last-day
  (with-redefs [date/current-date (fn [] (date/parse-date today))]
    (is (= true  (issue-filters/changed-state-in-the-last-day? (issue-modified-at (hours-offset -6)))))
    (is (= true  (issue-filters/changed-state-in-the-last-day? (issue-modified-at (hours-offset -12)))))
    (is (= false (issue-filters/changed-state-in-the-last-day? (issue-modified-at (hours-offset -48)))))
    (is (= false (issue-filters/changed-state-in-the-last-day? {})))))
