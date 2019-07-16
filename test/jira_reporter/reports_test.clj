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

(defn- test-today []
  (parse-date today))

(defn- containing-issues? [expected actual]
  (let [actual-rows (map (fn [x] (select-keys x (keys (first expected)))) (:rows actual))]
    (= expected actual-rows)))

(deftest report-work-delivered-then-reports-stories-and-orphan-tasks-delivered
  (with-redefs [date/today test-today
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

;; -----------------------------------------------------------------------------
;; Burndown
;; -----------------------------------------------------------------------------

;; January
;; --------------------------
;; Mo  Tu  We  Th  Fr  Sa  Su
;;                     1   2
;; 3   4   5   6   7   8   9
;; 10  11  12  13  14  15  16
;; 17  18  19  20  21  22  23
;; 24  25  26  27  28  29  30
;; 31  

;; February 2000
;; --------------------------
;; Mo  Tu  We  Th  Fr  Sa  Su
;;     1   2   3   4   5   6
;; 7   8   9   10  11  12  13
;; 14  15  16  17  18  19  20
;; 21  22  23  24  25  26  27
;; 28  29          

(defn- delivered-issue [id date points]
  (issue id "closed" :history [(status-change date "closed")] :points points))

(def- sprint-issues
  [(delivered-issue "1"  "2000-01-27" 3)
   (delivered-issue "2"  "2000-01-28" 3)
   (delivered-issue "3"  "2000-01-29" 3)
   (delivered-issue "4"  "2000-01-30" 3)
   (delivered-issue "5"  "2000-01-31" 3)
   (delivered-issue "6"  "2000-02-01" 3)
   (delivered-issue "7"  "2000-02-02" 3)
   (delivered-issue "8"  "2000-02-03" 3)
   (delivered-issue "9"  "2000-02-04" 3)
   (delivered-issue "10" "2000-02-05" 3)
   (delivered-issue "11" "2000-02-06" 3)])

(def- expected-burndown
  [{:date "2000-01-27" :open 11 :closed 0}
   {:date "2000-01-28" :open 10 :closed 1}
   {:date "2000-01-29" :open 9  :closed 2}
   {:date "2000-01-30" :open 8  :closed 3}
   {:date "2000-01-31" :open 7  :closed 4}
   {:date "2000-02-01" :open 6  :closed 5}
   {:date "2000-02-02" :open 5  :closed 6}])

(deftest report-burndown-then-generates-burndown
  (with-redefs [date/today test-today
                config     test-config]
    (let [start-date    (parse-date "2000-01-27")
          end-date      (parse-date "2000-02-10")]
      (is (= expected-burndown (:rows (report-burndown start-date end-date sprint-issues)))))))
