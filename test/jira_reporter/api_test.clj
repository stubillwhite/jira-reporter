(ns jira-reporter.api-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [jira-reporter.api :refer :all]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time ZonedDateTime ZoneId]))

(def current-sprint-json
  {:body (slurp (io/resource "test-current-sprint.json"))})

(def- test-config
  {:jira {:username "test-username"
          :password "test-password"
          :server   "test.url.com"
          :project  "test-project"}})

(def- expected-current-sprint-issues
  [{:id        "issue-1-key"
    :parent-id "issue-1-parent"
    :type      "issue-1-type"
    :title     "issue-1-title"
    :status    "issue-1-status"
    :assignee  "issue-1-assignee"},
   {:id        "issue-2-key"
    :parent-id "issue-2-parent"
    :type      "issue-2-type"
    :title     "issue-2-title"
    :status    "issue-2-status"
    :assignee  "issue-2-assignee"}])

(deftest get-issues-in-current-sprint-then-issue-status-of-open-sprint
  (with-redefs [api-get-jql-query (fn [config _] current-sprint-json)] 
    (is (= expected-current-sprint-issues (get-issues-in-current-sprint test-config)))))

(def issue-details-json
  {:body (slurp (io/resource "test-issue-details.json"))})

(defn- utc-date-time [y m d]
  (ZonedDateTime/of y m d 0 0 0 0 (ZoneId/of "UTC")))

(def- expected-issue-details
  [{:date  (utc-date-time 1970 1 1)
    :field "status"
    :from  "To Do"
    :to    "In Progress"}])

(deftest get-issue-details-then-details
  (let [stub-id "stub-id"]
    (with-redefs [api-get-issue-details (fn [config id] (when (= id stub-id) issue-details-json))] 
      (is (= expected-issue-details (get-issue-details test-config stub-id))))))
