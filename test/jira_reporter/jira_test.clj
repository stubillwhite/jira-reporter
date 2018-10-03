(ns jira-reporter.jira-test
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [jira-reporter.jira :as jira :refer :all]
            [jira-reporter.rest-client :as rest-client]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time ZonedDateTime ZoneId]))

(def current-sprint-json
  (rest-client/decode-body {:body (slurp (io/resource "test-current-sprint.json"))}))

(def- stub-test-config
  {:jira {:username "test-username"
          :password "test-password"
          :server   "test.url.com"
          :project  "test-project"}})

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

(defn- stub-board [id name]
  {:id   id
   :name name})

(defn- stub-sprint [id state]
  {:id    id
   :state state})

(def- stub-config
  {:jira {:board "board-1-name"}})

(deftest get-issues-in-current-sprint-then-decodes-issues
  (with-redefs [rest-client/get-boards            (fn [cfg] [(stub-board 1 "board-1-name") (stub-board 2 "board-2-name")])
                rest-client/get-sprints-for-board (fn [cfg id] (when (= 1 id) [(stub-sprint 1 "active") (stub-sprint 2 "inactive")]))
                rest-client/get-issues-for-sprint (fn [cfg id] (when (= 1 id) current-sprint-json))]
    (is (= expected-current-sprint-issues (get-issues-in-current-sprint stub-config)))))

