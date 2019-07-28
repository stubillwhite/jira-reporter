(ns jira-reporter.date-test
  (:require [clojure.test :refer :all]
            [jira-reporter.date :as date :refer :all]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time ZonedDateTime ZoneId]
           java.time.temporal.ChronoUnit))

;; January 2000
;; --------------------------
;; Mo  Tu  We  Th  Fr  Sa  Su
;;                     1   2
;; 3   4   5   6   7   8   9
;; 10  11  12  13  14  15  16
;; 17  18  19  20  21  22  23
;; 24  25  26  27  28  29  30
;; 31  

;; February 2000
;; --------------------------
;; Mo  Tu  We  Th  Fr  Sa  Su
;;     1   2   3   4   5   6
;; 7   8   9   10  11  12  13
;; 14  15  16  17  18  19  20
;; 21  22  23  24  25  26  27
;; 28  29          

(deftest test-parse-date-time
  (testing "parses ISO 8601 date time"
    (let [expected (ZonedDateTime/of 2000 1 1 0 0 0 0 utc)]
      (is (= expected (date/parse-date-time "2000-01-01T00:00Z"))))))

(deftest test-parse-date
  (testing "parses ISO 8601 date"
    (let [expected (ZonedDateTime/of 2000 1 1 0 0 0 0 utc)]
      (is (= expected (date/parse-date "2000-01-01Z"))))))

(deftest test-timestream
  (testing "increments by n units per step"
    (let [expected (map date/parse-date ["2000-01-01Z" "2000-01-03Z" "2000-01-05Z"])]
      (is (= expected
             (take 3 (date/timestream (date/parse-date "2000-01-01Z") 2 ChronoUnit/DAYS)))))))

(deftest test-working-day?
  (testing "given non-working day then false"
    (is (= false (date/working-day? (date/parse-date "2000-01-01Z")))))
  (testing "given working day then true"
    (is (= true (date/working-day? (date/parse-date "2000-01-03Z"))))))

(deftest test-working-hour?
  (testing "given non-working day and working hour then false"
    (is (= false (date/working-hour? (date/parse-date-time "2000-01-01T12:00Z")))))
  (testing "given working day and non-working hour then false"
    (is (= false (date/working-hour? (date/parse-date-time "2000-01-03T00:00Z")))))
  (testing "given working day and working hour then true"
    (is (= true (date/working-hour? (date/parse-date-time "2000-01-03T12:00Z"))))))

(defn- utc-date-time
  ([d]
   (ZonedDateTime/of 2000 2 d 0 0 0 0 utc))
  ([d h m]
   (ZonedDateTime/of 2000 2 d h m 0 0 utc)))

(deftest test-working-days-between
  (testing "given range spans weekend then counts only working days"
    (let [t1 (utc-date-time 1)
          t2 (utc-date-time 15)]
      (is (= 10 (working-days-between t1 t2)))
      (is (= 10 (working-days-between t2 t1)))))
  (testing "given less than one day then rounds up to one day"
    (let [t1 (utc-date-time 5)
          t2 (utc-date-time 5)]
      (is (= 1 (working-days-between t2 t1)))))
  (testing "given yesterday then one day"
    (let [t1 (utc-date-time 7)
          t2 (utc-date-time 8)]
      (is (= 1 (working-days-between t2 t1))))))

(deftest test-working-hours-between
  (testing "given spans non-working hours then counts only working hours and rounds down"
    (let [t1 (utc-date-time 25 7  0)
          t2 (utc-date-time 25 20 45)]
      (is (= 7 (working-hours-between t2 t1)))))
  (testing "given spans weekends then counts only working hours and rounds down"
    (let [t1 (utc-date-time 25 15 0)
          t2 (utc-date-time 28 11 45)]
      (is (= 4 (working-hours-between t2 t1)))))
  (testing "given less than one then rounds down to zero"
    (let [t1 (utc-date-time 1 9  0)
          t2 (utc-date-time 1 9 45)]
      (is (= 0 (working-hours-between t2 t1)))))
  (testing "given more than one then rounds down"
    (let [t1 (utc-date-time 1 9  0)
          t2 (utc-date-time 1 11 45)]
      (is (= 2 (working-hours-between t2 t1))))))
