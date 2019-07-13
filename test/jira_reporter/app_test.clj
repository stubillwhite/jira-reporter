(ns jira-reporter.app-test
  (:require [jira-reporter.analysis :as analysis]
            [jira-reporter.app :refer :all]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.jira :as jira]
            [jira-reporter.test-common :refer :all]
            [jira-reporter.utils :refer [def-]]))

;; (-main "-h")
;; (-main "--sprint-name" "Sprint 13-18: New blood")


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

;; (with-redefs [jira/get-issues-in-current-sprint (fn [config] all-issues)
;;               analysis/add-derived-fields       (fn [x] x)
;;               date/today                        test-today
;;               config                            test-config
;;               
;;   (println (jira/get-issues-in-current-sprint))]
;;   (println (map analysis/add-derived-fields (jira/get-issues-in-current-sprint)))
;;   (-main))
