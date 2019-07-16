(ns jira-reporter.jira-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [jira-reporter.config :as config]
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
          :board    "test-board"}})

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
  [{:id          "issue-1-key"
    :created     (utc-date-time 1970 1 1)
    :parent-id   "issue-1-parent"
    :subtask-ids []
    :points      nil
    :type        "issue-1-type"
    :title       "issue-1-title"
    :status      "issue-1-status"
    :assignee    "issue-1-assignee"
    :history     expected-issue-1-history},
   {:id          "issue-2-key"
    :created     (utc-date-time 1970 1 1)
    :parent-id   "issue-2-parent"
    :subtask-ids ["issue-2-subtask-1" "issue-2-subtask-2"]
    :points      "3"
    :type        "issue-2-type"
    :title       "issue-2-title"
    :status      "issue-2-status"
    :assignee    "issue-2-assignee"
    :history     expected-issue-2-history}])

(defn- stub-board [id name]
  {:id   id
   :name name})

(def- stub-boards [(stub-board 1 "board-1-name") (stub-board 2 "board-2-name")])

(defn- stub-sprint [id name state]
  {:id    id
   :name  name
   :state state})

(def- stub-sprints [(stub-sprint 1 "stub-sprint-1-name" "active") (stub-sprint 2 "stub-sprint-2-name" "inactive")])

;; TODO: This is wrong
(def- stub-config
  {:jira {:board "board-1-name"}})

(deftest get-issues-in-current-sprint-then-decodes-issues
  (with-redefs [config/config                     stub-config
                rest-client/get-boards            (fn [cfg] stub-boards)
                rest-client/get-sprints-for-board (fn [cfg id] (when (= 1 id) stub-sprints))
                rest-client/get-issues-for-sprint (fn [cfg id] (when (= 1 id) current-sprint-json))]
    (is (= expected-current-sprint-issues (get-issues-in-current-sprint)))))

(deftest get-sprint-names-then-returns-sprint-names
  (with-redefs [config/config                     stub-config
                rest-client/get-boards            (fn [cfg] stub-boards)
                rest-client/get-sprints-for-board (fn [cfg id] (when (= 1 id) stub-sprints))]
    (is (= ["stub-sprint-1-name" "stub-sprint-2-name"] (get-sprint-names "board-1-name")))))

(deftest get-issues-in-sprint-named-then-decodes-issues
  (with-redefs [config/config                     stub-config
                rest-client/get-boards            (fn [cfg] stub-boards)
                rest-client/get-sprints-for-board (fn [cfg id] (when (= 1 id) stub-sprints))
                rest-client/get-issues-for-sprint (fn [cfg id] (when (= 1 id) current-sprint-json))]
    (is (= expected-current-sprint-issues (get-issues-in-sprint-named "stub-sprint-1-name")))))
