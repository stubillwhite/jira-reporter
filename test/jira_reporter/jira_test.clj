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

(def- expected-previous-sprint
  {:id         "sprint-1-id"
   :name       "sprint-1-name"
   :state      "sprint-1-state"
   :start-date (utc-date-time 1970 1 1)
   :end-date   (utc-date-time 1970 1 1)})

(def- expected-current-sprint
  {:id         "sprint-2-id"
   :name       "sprint-2-name"
   :state      "sprint-2-state"
   :start-date (utc-date-time 1970 1 1)
   :end-date   (utc-date-time 1970 1 1)})

(def- expected-current-sprint-issues
  [{:id             "issue-1-key"
    :created        (utc-date-time 1970 1 1)
    :parent-id      "issue-1-parent"
    :subtask-ids    []
    :points         nil
    :team           nil
    :epic           nil
    :type           "issue-1-type"
    :title          "issue-1-title"
    :status         "issue-1-status"
    :assignee       "issue-1-assignee"
    :buddies        ["issue-1-buddy-1" "issue-1-buddy-2"]
    :labels         ["issue-1-label-1" "issue-1-label-2"]
    :current-sprint expected-current-sprint
    :closed-sprints [expected-previous-sprint]
    :history        expected-issue-1-history},
   {:id             "issue-2-key"
    :created        (utc-date-time 1970 1 1)
    :parent-id      "issue-2-parent"
    :subtask-ids    ["issue-2-subtask-1" "issue-2-subtask-2"]
    :points         3.0
    :team           "issue-2-team"
    :epic           "issue-2-epic"
    :type           "issue-2-type"
    :title          "issue-2-title"
    :status         "issue-2-status"
    :assignee       "issue-2-assignee"
    :buddies        []
    :labels         []
    :current-sprint expected-current-sprint
    :closed-sprints [expected-previous-sprint]
    :history        expected-issue-2-history}])

(defn- stub-board [id name]
  {:id   id
   :name name})

(def- stub-boards [(stub-board "1" "board-1-name") (stub-board "2" "board-2-name")])

(defn- stub-raw-sprint [id name state]
  {:id         id
   :name       name
   :state      state
   :startDate  (utc-date-time 1970 1 1)
   :endDate    (utc-date-time 1970 1 1)})

(defn- stub-extracted-sprint [id name state]
  {:id          id
   :name        name
   :state       state
   :start-date  (utc-date-time 1970 1 1)
   :end-date    (utc-date-time 1970 1 1)})

(def- stub-raw-sprints
  [(stub-raw-sprint "1" "stub-sprint-1-name" "active") (stub-raw-sprint "2" "stub-sprint-2-name" "inactive")])

(def- stub-extracted-sprints
  [(stub-extracted-sprint "1" "stub-sprint-1-name" "active") (stub-extracted-sprint "2" "stub-sprint-2-name" "inactive")])

(def- stub-config
  {:jira           {:board "board-1-name"}
   :cache-filename "target/cached-data.edn"
   :custom-fields  {:epic-link    "customfield_10236"
                    :team         "customfield_12604"
                    :story-points "customfield_10002"
                    :buddy        "customfield_12811"}})

(deftest get-sprints-then-returns-sprints
  (with-redefs [config/config                     stub-config
                rest-client/get-boards            (fn [] stub-boards)
                rest-client/get-sprints-for-board (fn [id] (when (= "1" id) stub-raw-sprints))]
    (is (= stub-extracted-sprints (get-sprints "board-1-name")))))

(deftest get-issues-in-sprint-named-then-decodes-issues
  (with-redefs [config/config                     stub-config
                rest-client/get-boards            (fn [] stub-boards)
                rest-client/get-sprints-for-board (fn [id] (when (= "1" id) stub-raw-sprints))
                rest-client/get-issues-for-sprint (fn [id] (when (= "1" id) current-sprint-json))]
    (is (= expected-current-sprint-issues (get-issues-in-sprint-named "board-1-name" "stub-sprint-1-name")))))

