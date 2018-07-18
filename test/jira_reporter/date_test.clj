(ns jira-reporter.date-test
  (:require [clojure.test :refer :all]
            [jira-reporter.date :refer :all])
  (:import [java.time ZonedDateTime ZoneId]))

(defn- utc-date-time [y m d]
  (ZonedDateTime/of y m d 0 0 0 0 (ZoneId/of "UTC")))

(deftest plus-workdays-given-positive-n-then-skips-weekends
  (is (= (utc-date-time 2000 1 4)  (plus-workdays (utc-date-time 2000 1 3) 1)))
  (is (= (utc-date-time 2000 1 7)  (plus-workdays (utc-date-time 2000 1 3) 4)))
  (is (= (utc-date-time 2000 1 10) (plus-workdays (utc-date-time 2000 1 3) 5))))

(deftest plus-workdays-given-negative-n-then-skips-weekends
  (is (= (utc-date-time 2000 2 24) (plus-workdays (utc-date-time 2000 2 25) -1)))
  (is (= (utc-date-time 2000 2 21) (plus-workdays (utc-date-time 2000 2 25) -4)))
  (is (= (utc-date-time 2000 2 18) (plus-workdays (utc-date-time 2000 2 25) -5))))

(deftest workdays-between-then-skips-weekends
  (let [t1 (utc-date-time 2000 2 1)
        t2 (utc-date-time 2000 2 15)]
    (is (= 10 (workdays-between t1 t2)))
    (is (= 10 (workdays-between t2 t1)))))
