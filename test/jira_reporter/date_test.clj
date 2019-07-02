(ns jira-reporter.date-test
  (:require [clojure.test :refer :all]
            [jira-reporter.date :refer :all]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time ZonedDateTime ZoneId]))

(def- utc (ZoneId/of "UTC"))

(defn- utc-date-time
  ([d]
   (ZonedDateTime/of 2000 2 d 0 0 0 0 utc))
  ([d h m]
   (ZonedDateTime/of 2000 2 d h m 0 0 utc)))

;; February 2000
;; --------------------------
;; Mo  Tu  We  Th  Fr  Sa  Su
;;     1   2   3   4   5   6
;; 7   8   9   10  11  12  13
;; 14  15  16  17  18  19  20
;; 21  22  23  24  25  26  27
;; 28  29          

(deftest plus-working-days-given-positive-n-then-skips-weekends
  (is (= (utc-date-time 22) (plus-working-days (utc-date-time 21) 1)))
  (is (= (utc-date-time 25) (plus-working-days (utc-date-time 21) 4)))
  (is (= (utc-date-time 28) (plus-working-days (utc-date-time 21) 5))))

(deftest plus-working-days-given-negative-n-then-skips-weekends
  (is (= (utc-date-time 24) (plus-working-days (utc-date-time 25) -1)))
  (is (= (utc-date-time 21) (plus-working-days (utc-date-time 25) -4)))
  (is (= (utc-date-time 18) (plus-working-days (utc-date-time 25) -5))))

(deftest working-days-between-then-skips-weekends
  (let [t1 (utc-date-time 1)
        t2 (utc-date-time 15)]
    (is (= 10 (working-days-between t1 t2)))
    (is (= 10 (working-days-between t2 t1)))))

(deftest working-days-between-given-less-than-one-then-one
  (let [t1 (utc-date-time 5)
        t2 (utc-date-time 5)]
    (is (= 1 (working-days-between t2 t1)))))

(deftest working-days-between-given-yesterday-then-one
  (let [t1 (utc-date-time 7)
        t2 (utc-date-time 8)]
    (is (= 1 (working-days-between t2 t1)))))

(deftest working-hours-between-then-skips-non-working-hours-and-rounds-down
  (let [t1 (utc-date-time 25 7  0)
        t2 (utc-date-time 25 20 45)]
    (is (= 7 (working-hours-between t2 t1)))))

(deftest working-hours-between-then-skips-weekends-and-rounds-down
  (let [t1 (utc-date-time 25 15 0)
        t2 (utc-date-time 28 11 45)]
    (is (= 4 (working-hours-between t2 t1)))))

(deftest working-hours-between-given-less-than-one-then-zero
  (let [t1 (utc-date-time 1 9  0)
        t2 (utc-date-time 1 9 45)]
    (is (= 0 (working-hours-between t2 t1)))))

(deftest working-hours-between-then-rounds-down
  (let [t1 (utc-date-time 1 9  0)
        t2 (utc-date-time 1 11 45)]
    (is (= 2 (working-hours-between t2 t1)))))
