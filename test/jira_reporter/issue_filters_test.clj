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

(defn group-by-story [issues]
  (let [stories (group-by :id (stories issues))
        tasks   (group-by :id (tasks issues))]
    (for [k (concat (keys stories) (keys tasks))]
      [(get stories k) (get tasks k)])))

;; TODO: Reorder

;; (deftest group-by-story-then-FOO
;;   (let [stub-story   (fn [story-id]         {:type "Story" :id story-id})
;;         stub-task    (fn [task-id story-id] {:type "Task"  :id task-id :parent-id story-id})
;;         stub-story-1 (stub-story "story-1")
;;         stub-task-1  (stub-task  "task-1" "story-1")
;;         stub-task-2  (stub-task  "task-2" "story-1")
;;         stub-task-3  (stub-task  "task-3" "story-1")
;;         stub-story-2 (stub-story "story-2")
;;         stub-task-4  (stub-task  "task-4" "story-2")
;;         stub-task-5  (stub-task "task-5" nil)
;;         issues       [stub-story-1 stub-story-2 stub-task-1 stub-task-2 stub-task-3 stub-task-4 stub-task-5]]
;;     (is (=  {stub-story-1 [stub-task-1 stub-task-2 stub-task-3]
;;                                     stub-story-2 [stub-task-4]
;;              nil          [stub-task-5]}
;;             (group-by-story issues)))))


