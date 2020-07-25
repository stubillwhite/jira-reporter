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

;; (deftest issue-at-date
;;   (testing "given created after date then nil"
;;     (is (nil? (issue-filters/issue-at-date date-6th {:created date-7th}))))
  
;;   (testing "given status changed then can restore previous history"
;;     (let [history  [(status-change str-6th "in-progress") (status-change str-7th "closed")]
;;           issue    (story "1" "closed" :created date-5th :history history)
;;           expected (-> issue (assoc :status "in-progress" :history (drop-last history)))]
;;       (is (= expected (issue-filters/issue-at-date date-6th issue)))))

;;   (testing "problem case"
;;     (let [history  [(status-change-x "2020-07-01T09:05:57Z" "in-progress") (status-change-x "2020-07-01T13:10:29Z" "closed")]
;;           issue    (story "1" "closed" :created (date/parse-date-time "2020-06-25T11:56:45Z") :history history)
;;           expected (-> issue (assoc :status "todo" :history []))]
;;       (is (= expected (issue-filters/issue-at-date (date/parse-date-time "2020-06-25T15:59:14.608Z") issue)))))
  
;;   (testing "given non-status field changed then ignores changes"
;;     (let [history  [(type-change str-5th "epic" "story")
;;                     (status-change str-6th "in-progress")
;;                     (status-change str-7th "closed")]
;;           issue    (story "1" "closed" :created date-5th :history history)
;;           expected (-> issue (assoc :status "in-progress" :history (drop-last history)))]
;;       (is (= expected (issue-filters/issue-at-date date-6th issue)))))
;;   )

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


;; {:history
;;  ({:date
;;    #object[java.time.ZonedDateTime 0x42afbe50 "2020-07-01T09:05:57Z[UTC]"],
;;    :field "status",
;;    :from "To Do",
;;    :to "In Progress"}
;;   {:date
;;    #object[java.time.ZonedDateTime 0x7710d17c "2020-07-01T13:10:29Z[UTC]"],
;;    :field "status",
;;    :from "In Progress",
;;    :to "Closed - DONE"}),
;;  :type "Task",
;;  :created
;;  #object[java.time.ZonedDateTime 0xc098de1 "2020-06-25T11:56:45Z[UTC]"],
;;  :status "Closed - DONE",
;;  :id "SDPR-3840",
;; }
;; #object[java.time.ZonedDateTime 0x9f5d69e "2020-06-25T15:59:14.608Z[UTC]"]


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
      (is (= false (issue-filters/open? {:status "closed"}))))
    (testing "assigned?"
      (is (= true (issue-filters/assigned? {:assignee "someone"})))
      (is (= false (issue-filters/assigned? {:assignee nil}))))
    (testing "has-labels?"
      (is (= true (issue-filters/has-labels? [:foo :bar] {:labels #{:foo :bar :baz}})))
      (is (= false (issue-filters/has-labels? [:foo :bar] {:labels #{:foo :baz}}))))))

(deftest changed-state-in-the-last-day
  (with-redefs [date/current-date (fn [] date-7th)]
    (is (= true  (issue-filters/changed-state-in-the-last-day? (issue-modified-at (hours-offset -6)))))
    (is (= true  (issue-filters/changed-state-in-the-last-day? (issue-modified-at (hours-offset -12)))))
    (is (= false (issue-filters/changed-state-in-the-last-day? (issue-modified-at (hours-offset -48)))))
    (is (= false (issue-filters/changed-state-in-the-last-day? {})))))
