(ns jira-reporter.api-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [jira-reporter.api :refer :all]
            [jira-reporter.utils :refer [def-]]
            [jira-reporter.jira-client :as jira-client]
            [jira-reporter.api :as api])
  (:import [java.time ZonedDateTime ZoneId])
  )

(def current-sprint-json
  {:body (slurp (io/resource "test-current-sprint.json"))})

(def issue-details-1-json
  {:body (slurp (io/resource "test-issue-1-details.json"))})

(def issue-details-2-json
  {:body (slurp (io/resource "test-issue-2-details.json"))})

(def- stub-test-config
  {:jira {:username "test-username"
          :password "test-password"
          :server   "test.url.com"
          :project  "test-project"}})

(defn stub-get-jira-query-results [config query]
  current-sprint-json)

(defn stub-get-issue-details [config id]
  (get {"issue-1-key" issue-details-1-json
        "issue-2-key" issue-details-2-json} id))

(defn- utc-date-time [y m d]
  (ZonedDateTime/of y m d 0 0 0 0 (ZoneId/of "UTC")))

(def- expected-issue-1-history
  [{:date  (utc-date-time 1970 1 1)
    :field "status"
    :from  "To Do"
    :to    "In Progress"}])

(def- expected-issue-2-history
  [{:date  (utc-date-time 1970 1 1)
    :field "status"
    :from  "To Do"
    :to    "In Progress"}
   {:date  (utc-date-time 1970 1 2)
    :field "status"
    :from  "In Progress"
    :to    "Closed"}])

(def- expected-current-sprint-issues
  [{:id        "issue-1-key"
    :parent-id "issue-1-parent"
    :type      "issue-1-type"
    :title     "issue-1-title"
    :status    "issue-1-status"
    :assignee  "issue-1-assignee"
    :history   expected-issue-1-history},
   {:id        "issue-2-key"
    :parent-id "issue-2-parent"
    :type      "issue-2-type"
    :title     "issue-2-title"
    :status    "issue-2-status"
    :assignee  "issue-2-assignee"
    :history   expected-issue-2-history}])

(deftest get-issues-in-current-sprint-then-issue-status-of-open-sprint
  (with-redefs [jira-client/get-jql-query-results stub-get-jira-query-results
                jira-client/get-issue-details     stub-get-issue-details] 
    (is (= expected-current-sprint-issues (get-issues-in-current-sprint stub-test-config)))))
