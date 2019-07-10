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

(def test-config
  {:schema {:to-do-states            #{"to-do"}
            :in-progress-states      #{"in-progress"}
            :blocked-states          #{"blocked"}
            :closed-states           #{"closed"}
            :deployment-states       #{"deploy"}
            :story-types             #{"story"}
            :task-types              #{"task"}
            :bug-types               #{"bug"}
            :gdpr-types              #{"gpdr"}
            :story-closed-state      "Closed"
            :story-open-state        "To Do"
            :story-in-progress-state "In Progress"}
   })

(defn stub-issue [id type status & {:keys [subtasks history points]
                               :or {subtasks []
                                    history  []
                                    points   nil}}]
  {:id       id
   :type     type
   :status   status
   :subtasks subtasks
   :history  history
   :points   points})

(defn issue [id status & kvs] (apply stub-issue id "issue" status kvs))
(defn story [id status & kvs] (apply stub-issue id "story" status kvs))
(defn task  [id status & kvs] (apply stub-issue id "task"  status kvs))

(def- formatter
  (-> (DateTimeFormatterBuilder.)
      (.appendPattern "yyyy-MM-dd")
      (.parseDefaulting ChronoField/HOUR_OF_DAY 0)
      (.parseDefaulting ChronoField/MINUTE_OF_HOUR 0)
      (.parseDefaulting ChronoField/SECOND_OF_MINUTE 0)
      (.toFormatter)
      (.withZone (ZoneId/of "UTC"))))

(defn parse-date [s]
  (-> (.parse formatter s) (ZonedDateTime/from)))

(defn status-change [date to]
  (let [parsed-date (parse-date date)]
    {:date  parsed-date
     :field "status"
     :to    to}))




