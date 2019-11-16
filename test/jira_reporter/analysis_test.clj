(ns jira-reporter.analysis-test
  (:require [clojure.test :refer :all]
            [jira-reporter.analysis :refer :all]
            [jira-reporter.date :as date]
            [jira-reporter.config :refer [config]]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time ZonedDateTime ZoneId]))

(defn- add-transition [field new-value history t]
  (conj history {:date  t
                 :field field
                 :from  (-> history last :to)
                 :to    new-value}))

(def- type-change (partial add-transition "type" "story"))
(def- to-do       (partial add-transition "status" "To Do"))
(def- in-progress (partial add-transition "status" "In Progress"))
(def- blocked     (partial add-transition "status" "Blocked"))
(def- done        (partial add-transition "status" "Closed - DONE"))

(def- utc (ZoneId/of "UTC"))

(defn- utc-time
  ([h]
   (ZonedDateTime/of 2000 5 1 h 0 0 0 utc)))

(defn- utc-date
  ([d]
   (ZonedDateTime/of 2000 5 d 0 0 0 0 utc)))

(defn- stub-issue-with [m]
  (merge {:id                "stub-id"
          :created           (ZonedDateTime/now utc)
          :parent-id         "stub-parent-id"
          :subtask-ids       []
          :type              "stub-type"
          :status            "stub-status"
          :assignee          nil
          :title             "stub-title"
          :points            nil
          :epic              nil
          :history           []
          :lead-time-in-days nil
          :time-in-state     {}}
         m))

(def- stub-config
  {:cache-filename "target/cached-data.edn"
   :schema         {:to-do-states       #{"To Do"}
                    :in-progress-states #{"In Progress"}
                    :blocked-states     #{"Blocked"}
                    :closed-states      #{"Closed - DONE"}
                    }})

(deftest add-derived-fields-given-issue-closed-then-adds-time-in-state
  (with-redefs [config stub-config]
    (let [stub-closed-issue      (stub-issue-with {:history (-> []
                                                                (in-progress (utc-time 9))
                                                                (type-change (utc-time 9))
                                                                (blocked     (utc-time 11))
                                                                (in-progress (utc-time 12))
                                                                (done        (utc-time 13)))})
          expected-time-in-state {:in-progress 3 :blocked 1}]
      (is (= expected-time-in-state (:time-in-state (add-derived-fields stub-closed-issue)))))))

(deftest add-derived-fields-given-issue-open-then-adds-time-in-state
  (with-redefs [config                 stub-config
                date/current-date-time (fn [] (utc-time 16))]
    (let [stub-in-progress-issue (stub-issue-with {:history (-> []
                                                                (in-progress (utc-time 9))
                                                                (blocked     (utc-time 11))
                                                                (in-progress (utc-time 12)))})
          expected-time-in-state {:in-progress 5 :blocked 1}]
      (is (= expected-time-in-state (:time-in-state (add-derived-fields stub-in-progress-issue)))))))

(deftest add-derived-fields-given-issue-closed-then-adds-lead-time-in-days
  (with-redefs [config stub-config]
    (let [stub-closed-issue (stub-issue-with {:history (-> []
                                                           (in-progress (utc-date 2))
                                                           (blocked     (utc-date 4))
                                                           (in-progress (utc-date 4))
                                                           (done        (utc-date 5)))})]
      (is (= 4 (:lead-time-in-days (add-derived-fields stub-closed-issue)))))))

(deftest add-derived-fields-given-issue-open-then-adds-lead-time-in-days
  (with-redefs [config                 stub-config
                date/current-date-time (fn [] (utc-date 5))]
    (let [stub-in-progress-issue (stub-issue-with {:history (-> []
                                                                (in-progress (utc-date 1))
                                                                (blocked     (utc-date 3))
                                                                (in-progress (utc-date 3)))})]
      (is (= 5 (:lead-time-in-days (add-derived-fields stub-in-progress-issue)))))))
