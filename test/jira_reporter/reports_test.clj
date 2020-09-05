(ns jira-reporter.reports-test
  (:require [clojure.test :refer :all]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.reports :refer :all]
            [jira-reporter.test-common :refer :all]
            [jira-reporter.utils :refer [def-]]))

(def- two-days-ago "2000-02-01Z")
(def- yesterday    "2000-02-02Z")
(def- today        "2000-02-03Z")

(def- untouched-issue            (issue "1" "to-do"       :parent-id "23"))
(def- existing-in-progress-issue (issue "2" "in-progress" :parent-id "23" :history [(status-change two-days-ago "todo" "in-progress")]))
(def- newly-in-progress-issue    (issue "3" "in-progress" :parent-id "23" :history [(status-change yesterday    "in-progress" "in-progress")]))
(def- newly-closed-issue         (issue "4" "closed"      :parent-id "23" :history [(status-change yesterday    "in-progress" "closed")]))
(def- deploy-issue               (issue "5" "deploy"      :parent-id "23"))

(def- all-issues
  [untouched-issue
   existing-in-progress-issue
   newly-in-progress-issue
   newly-closed-issue
   deploy-issue])

(defn- date-today []
  (date/parse-date today))

(defn- containing-issues? [expected actual]
  (let [actual-rows (map (fn [x] (select-keys x (keys (first expected)))) (:rows actual))]
    (= expected actual-rows)))

(deftest report-issues-started-then-reports-issues-moved-to-in-progress-in-the-previous-day
  (with-redefs [date/current-date date-today
                config            stub-config]
    (is (containing-issues? [newly-in-progress-issue] (report-issues-started all-issues)))))

(deftest report-issues-in-progress-then-reports-issues-in-progress-which-did-not-start-in-the-previous-day
  (with-redefs [date/current-date date-today
                config            stub-config]
    (is (containing-issues? [existing-in-progress-issue] (report-issues-in-progress all-issues)))))

(deftest report-issues-ready-for-release-then-reports-issues-ready-to-deploy
  (with-redefs [date/current-date date-today
                config            stub-config]
    (is (containing-issues? [deploy-issue] (report-issues-ready-for-release all-issues)))))

;; -----------------------------------------------------------------------------
;; Sprint report
;; -----------------------------------------------------------------------------

;; January
;; --------------------------
;; Mo  Tu  We  Th  Fr  Sa  Su
;;                     1   2
;; 3   4   5   6   7   8   9
;; 10  11  12  13  14  15  16
;; 17  18  19  20  21  22  23
;; 24  25  26 [27]  28  29  30
;; 31  

;; February 2000
;; --------------------------
;; Mo  Tu  We  Th  Fr  Sa  Su
;;     1   2   3   4   5   6
;; 7   8   9  [10] 11  12  13
;; 14  15  16  17  18  19  20
;; 21  22  23  24  25  26  27
;; 28  29          

(defn- delivered-issue [id date-created date-closed points]
  (issue id "todo" :created (date/parse-date date-created) :history [(status-change date-closed "todo" "closed")] :points points))

(def- sprint-issues
  [(delivered-issue "1"  "2000-01-01Z" "2000-01-28Z" 3.0)
   (delivered-issue "2"  "2000-01-01Z" "2000-01-28Z" 3.0)
   (delivered-issue "3"  "2000-01-01Z" "2000-01-31Z" 3.0)
   (delivered-issue "4"  "2000-01-01Z" "2000-02-01Z" 3.0)
   (delivered-issue "5"  "2000-01-31Z" "2000-02-02Z" 3.0)
   (delivered-issue "6"  "2000-01-31Z" "2000-02-03Z" 3.0)
   (delivered-issue "7"  "2000-01-31Z" "2000-02-04Z" 3.0)])

;; (deftest report-work-delivered-then-reports-stories-and-orphan-tasks-delivered
;;   (with-redefs [date/current-date date-today
;;                 config            stub-config]
;;     (let [task-1  (task  "1" "to-do")
;;           task-2  (task  "2" "closed" :points 3)
;;           story-3 (story "3" "closed" :points 5)]
;;       (is (containing-issues? [task-2 story-3] (report-work-delivered [task-1 task-2 story-3]))))))

;; ;; TODO: Needed?
;; (deftest report-work-delivered-then-work-delivered-in-sprint
;;   (with-redefs [date/current-date test-today
;;                 config            test-config]
;;     (let [start-date    (date/parse-date "2000-01-27Z")
;;           end-date      (date/parse-date "2000-02-10Z")]
;;       (is (= [] (:rows (report-work-delivered start-date end-date sprint-issues)))))))

;; -----------------------------------------------------------------------------
;; Burndown
;; -----------------------------------------------------------------------------

;; TBD

;; -----------------------------------------------------------------------------
;; Backlog
;; -----------------------------------------------------------------------------

;; TBD
