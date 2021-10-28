(ns jira-reporter.issue-filters-test
  (:require [clojure.test :refer :all]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.issue-filters :as issue-filters]
            [jira-reporter.test-common :refer [stub-config story type-change status-change]]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time DayOfWeek ZonedDateTime ZoneId]
           java.time.temporal.ChronoUnit))

(def- str-4th "2000-01-04Z")
(def- str-5th "2000-01-05Z")
(def- str-6th "2000-01-06Z")
(def- str-7th "2000-01-07Z")

(def- date-4th (date/parse-date str-4th))
(def- date-5th (date/parse-date str-5th))
(def- date-6th (date/parse-date str-6th))
(def- date-7th (date/parse-date str-7th))

(defn- hours-offset [n]
  (.plus date-7th n ChronoUnit/HOURS))

(defn- issue-modified-at [t-mod]
  {:history [{:date t-mod}]})

(defn status-change-x [date to]
  (let [parsed-date (date/parse-date-time date)]
    {:date  parsed-date
     :field "status"
     :from  "todo"
     :to    to}))

(deftest issue-at-date
  (let [history  [(type-change str-5th "epic" "story") (status-change str-6th "todo" "in-progress") (status-change str-7th "in-progress" "closed")]
        issue    (story "1" "closed" :created date-5th :history history)]

    (testing "given created after date then nil"
      (is (= nil (issue-filters/issue-at-date date-4th issue))))

    (testing "given before any changes then initial state"
      (let [expected (-> issue (assoc :status "todo" :history (take 1 history)))]
        (is (= expected (issue-filters/issue-at-date date-5th issue)))))

    (testing "given after single change then changed state"
      (let [expected (-> issue (assoc :status "in-progress" :history (take 2 history)))]
        (is (= expected (issue-filters/issue-at-date date-6th issue)))))

    (testing "given after multiple changes then changed state"
      (is (= issue (issue-filters/issue-at-date date-7th issue))))))

;; TODO issues-at-date
;; (also remember to test when "issuetype" changes from "workflow" to "status" (not just status field))

(deftest basic-predicates
  (with-redefs [config stub-config]
    (testing "blocked?"
      (is (= true (issue-filters/blocked? {:status "blocked"})))
      (is (= false (issue-filters/blocked? {:status "not blocked"}))))
    (testing "in-progress?"
      (is (= true (issue-filters/in-progress? {:status "in-progress"})))
      (is (= false (issue-filters/in-progress? {:status "not in-progress"}))))
    (testing "awaiting-deployment?"
      (is (= true (issue-filters/awaiting-deployment? {:status "deploy"})))
      (is (= false (issue-filters/awaiting-deployment? {:status "not deploy"}))))
    (testing "epic"
      (is (= true (issue-filters/epic? {:type "epic"})))
      (is (= false (issue-filters/epic? {:type "not epic"}))))    
    (testing "story?"
      (is (= true (issue-filters/story? {:type "story"})))
      (is (= false (issue-filters/story? {:type "not story"}))))
    (testing "task?"
      (is (= true (issue-filters/task? {:type "task"})))
      (is (= false (issue-filters/task? {:type "not task"}))))
    (testing "subtask?"
      (is (= true (issue-filters/subtask? {:type "subtask"})))
      (is (= false (issue-filters/subtask? {:type "not subtask"}))))
    (testing "no-parent?"
      (is (= true (issue-filters/no-parent? {})))
      (is (= false (issue-filters/no-parent? {:parent-id "parent"}))))
    (testing "no-subtasks?"
      (is (= true (issue-filters/no-subtasks? {})))
      (is (= false (issue-filters/no-subtasks? {:subtask-ids ["subtask"]}))))
    (testing "business-deliverable?"
      (is (= true (issue-filters/business-deliverable? {})))
      (is (= false (issue-filters/business-deliverable? {:parent-id "parent"})))
      (is (= false (issue-filters/business-deliverable? {:labels #{"recs_pd"}}))))
    (testing "sized?"
      (is (= true (issue-filters/sized? {:points 1.0})))
      (is (= false (issue-filters/sized? {}))))
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
      (is (= false (issue-filters/open? {:status "closed"}))))
    (testing "assigned?"
      (is (= true (issue-filters/assigned? {:assignee "someone"})))
      (is (= false (issue-filters/assigned? {:assignee nil}))))
    (testing "has-labels?"
      (is (= true (issue-filters/has-labels? [:foo :bar] {:labels #{:foo :bar :baz}})))
      (is (= false (issue-filters/has-labels? [:foo :bar] {:labels #{:foo :baz}}))))
    (testing "buddied?"
      (is (= true (issue-filters/buddied? {:buddies ["someone"]})))
      (is (= false (issue-filters/buddied? {:buddies []}))))
    (testing "needs-size?"
      (is (= true (issue-filters/needs-size? {:type "task"})))
      (is (= false (issue-filters/needs-size? {:type "task" :points 1.0}))))
    (testing "needs-triage?"
      (is (= true (issue-filters/needs-triage? {:type "bug" :labels #{}})))
      (is (= false (issue-filters/needs-triage? {:type "task" :labels #{}})))
      (is (= false (issue-filters/needs-triage? {:type "bug" :labels #{"recs_triaged"}}))))
    (testing "breakage?"
      (is (= true (issue-filters/breakage? {:type "bug" :labels #{"recs_breakage"}})))
      (is (= false (issue-filters/breakage? {:type "bug" :labels #{}})))
      (is (= false (issue-filters/breakage? {:type "task" :labels #{}}))))
    (testing "data-science?"
      (is (= true (issue-filters/data-science? {:type "task" :labels #{"recs_ds"}})))
      (is (= false (issue-filters/data-science? {:type "task" :labels #{}}))))
    (testing "engineering?"
      (is (= true (issue-filters/engineering? {:type "task" :labels #{"recs_eng"}})))
      (is (= false (issue-filters/engineering? {:type "task" :labels #{}}))))
    (testing "infrastructure?"
      (is (= true (issue-filters/infrastructure? {:type "task" :labels #{"recs_infra"}})))
      (is (= false (issue-filters/infrastructure? {:type "task" :labels #{}}))))
    (testing "support?"
      (is (= true (issue-filters/support? {:type "task" :labels #{"recs_support"}})))
      (is (= false (issue-filters/support? {:type "task" :labels #{}}))))
    (testing "personal-development?"
      (is (= true (issue-filters/personal-development? {:type "task" :labels #{"recs_pd"}})))
      (is (= false (issue-filters/personal-development? {:type "task" :labels #{}}))))
    (testing "miscellaneous?"
      (is (= true (issue-filters/miscellaneous? {:type "task" :labels #{"recs_pd"}})))
      (is (= true (issue-filters/miscellaneous? {:type "gdpr" :labels #{}})))
      (is (= false (issue-filters/miscellaneous? {:type "task" :labels #{}}))))))

(deftest changed-state-in-the-last-day
  (with-redefs [date/current-date (fn [] date-7th)]
    (is (= true  (issue-filters/changed-state-in-the-last-day? (issue-modified-at (hours-offset -6)))))
    (is (= true  (issue-filters/changed-state-in-the-last-day? (issue-modified-at (hours-offset -12)))))
    (is (= false (issue-filters/changed-state-in-the-last-day? (issue-modified-at (hours-offset -48)))))
    (is (= false (issue-filters/changed-state-in-the-last-day? {})))))


