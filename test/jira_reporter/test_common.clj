(ns jira-reporter.test-common
  (:require [clojure.edn :as edn]
            [clojure.test :refer :all]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.reports :refer :all]
            [jira-reporter.utils :refer [def-]]
            [mount.core :as mount])
  (:import [java.time ZonedDateTime ZoneId]
           java.time.format.DateTimeFormatterBuilder
           java.time.temporal.ChronoField))

(def stub-config
  {:schema {:to-do-states            #{"to-do"}
            :in-progress-states      #{"in-progress"}
            :blocked-states          #{"blocked"}
            :closed-states           #{"closed"}
            :deployment-states       #{"deploy"}
            :story-types             #{"story"}
            :task-types              #{"task"}
            :bug-types               #{"bug"}
            :gdpr-types              #{"gdpr"}
            :story-closed-state      "Closed"
            :story-open-state        "To Do"
            :story-in-progress-state "In Progress"}})

(defn stub-issue [id type status & {:keys [created subtasks history points]
                                    :or {created  (date/parse-date "2000-01-01Z")
                                         subtasks []
                                         history  []
                                         points   nil}}]
  {:id       id
   :type     type
   :status   status
   :created  created
   :subtasks subtasks
   :history  history
   :points   points})

(defn issue [id status & kvs] (apply stub-issue id "issue" status kvs))
(defn story [id status & kvs] (apply stub-issue id "story" status kvs))
(defn task  [id status & kvs] (apply stub-issue id "task"  status kvs))

(defn status-change [date to]
  (let [parsed-date (date/parse-date date)]
    {:date  parsed-date
     :field "status"
     :from  "todo"
     :to    to}))
