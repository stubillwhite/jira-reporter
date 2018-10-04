(ns jira-reporter.date-test
  (:require [clojure.test :refer :all]
            [jira-reporter.date :refer :all]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time ZonedDateTime ZoneId]))

(def- utc (ZoneId/of "UTC"))

(defn- utc-date-time
  ([m d]
   (ZonedDateTime/of 2000 m d 0 0 0 0 utc))
  ([m d h]
   (ZonedDateTime/of 2000 m d h 0 0 0 utc)))

(deftest plus-working-days-given-positive-n-then-skips-weekends
  (is (= (utc-date-time 1 4)  (plus-working-days (utc-date-time 1 3) 1)))
  (is (= (utc-date-time 1 7)  (plus-working-days (utc-date-time 1 3) 4)))
  (is (= (utc-date-time 1 10) (plus-working-days (utc-date-time 1 3) 5))))

(deftest plus-working-days-given-negative-n-then-skips-weekends
  (is (= (utc-date-time 2 24) (plus-working-days (utc-date-time 2 25) -1)))
  (is (= (utc-date-time 2 21) (plus-working-days (utc-date-time 2 25) -4)))
  (is (= (utc-date-time 2 18) (plus-working-days (utc-date-time 2 25) -5))))

(deftest working-days-between-then-skips-weekends
  (let [t1 (utc-date-time 2 1)
        t2 (utc-date-time 2 15)]
    (is (= 10 (working-days-between t1 t2)))
    (is (= 10 (working-days-between t2 t1)))))

(deftest working-hours-between-then-skips-non-working-hours
  (let [t1 (utc-date-time 2 1 7)
        t2 (utc-date-time 2 1 20)]
    (is (= 7 (working-hours-between t2 t1)))))

(deftest working-hours-between-then-skips-weekends
  (let [t1 (utc-date-time 2 4 15)
        t2 (utc-date-time 2 7 11)]
    (is (= 4 (working-hours-between t2 t1)))))
