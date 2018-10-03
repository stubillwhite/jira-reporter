(ns jira-reporter.analysis-test
  (:require [clojure.test :refer :all]
            [jira-reporter.analysis :refer :all]
            [jira-reporter.date :as date]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time ZonedDateTime ZoneId]))

 (defn- add-transition [new-state history t]
   (conj history {:date  t
                  :from  (-> history last :to)
                  :to    new-state}))

 (def- to-do       (partial add-transition "To Do"))
 (def- in-progress (partial add-transition "In Progress"))
 (def- blocked     (partial add-transition "Blocked"))
 (def- done        (partial add-transition "Closed - DONE"))

 (def- utc (ZoneId/of "UTC"))

 (defn- utc-time
   ([h]
    (ZonedDateTime/of 2000 5 1 h 0 0 0 utc)))

 (defn- utc-date
   ([d]
    (ZonedDateTime/of 2000 5 d 0 0 0 0 utc)))

(deftest add-derived-fields-given-issue-closed-then-adds-time-in-state
  (let [stub-closed-issue      {:history (-> []
                                             (in-progress (utc-time 9))
                                             (blocked     (utc-time 11))
                                             (in-progress (utc-time 12))
                                             (done        (utc-time 13)))}
        expected-time-in-state {:in-progress 3 :blocked 1}]
    (is (= expected-time-in-state (:time-in-state (add-derived-fields stub-closed-issue))))))

(deftest add-derived-fields-given-issue-open-then-adds-time-in-state
  (with-redefs [date/now (fn [] (utc-time 16))]
    (let [stub-in-progress-issue {:history (-> []
                                               (in-progress (utc-time 9))
                                               (blocked     (utc-time 11))
                                               (in-progress (utc-time 12)))}
          expected-time-in-state {:in-progress 5 :blocked 1}]
      (is (= expected-time-in-state (:time-in-state (add-derived-fields stub-in-progress-issue)))))))

(deftest add-derived-fields-given-issue-closed-then-adds-lead-time-in-days
  (let [stub-closed-issue {:history (-> []
                                        (in-progress (utc-date 2))
                                        (blocked     (utc-date 4))
                                        (in-progress (utc-date 4))
                                        (done        (utc-date 5)))}]
     (is (= 4 (:lead-time-in-days (add-derived-fields stub-closed-issue))))))

(deftest add-derived-fields-given-issue-open-then-adds-lead-time-in-days
  (with-redefs [date/now (fn [] (utc-date 5))]
    (let [stub-in-progress-issue {:history (-> []
                                               (in-progress (utc-date 1))
                                               (blocked     (utc-date 3))
                                               (in-progress (utc-date 3)))}]
      (is (= 5 (:lead-time-in-days (add-derived-fields stub-in-progress-issue)))))))
