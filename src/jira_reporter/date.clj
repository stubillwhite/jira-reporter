(ns jira-reporter.date
  (:require [jira-reporter.utils :refer [def-]])
  (:import [java.time DayOfWeek ZonedDateTime ZoneId]
           java.time.format.DateTimeFormatterBuilder
           [java.time.temporal ChronoField ChronoUnit]))

;; TODO: Docs and tests
(defn timestream [t n unit]
  (iterate (fn [x] (.plus x n unit)) t))

(defn working-day? [t]
  (not (contains? #{DayOfWeek/SATURDAY DayOfWeek/SUNDAY} (.getDayOfWeek t))))

(defn- before? [t]
  (fn [x] (.isBefore (.toInstant x) (.toInstant t))))

(defn working-hour? [t]
  (let [hour (.getHour t)]
    (and (<= 9 hour 16) (not (= 13 hour)))))

(def- zone-utc (ZoneId/of "UTC"))

(defn- with-zero-minutes [d]
  (.withMinute d 0))

(defn- with-zero-hours [d]
  (-> d (.withHour 0) (.withMinute 0)))

(def- formatter
  (-> (DateTimeFormatterBuilder.)
      (.appendPattern "yyyy-MM-dd")
      (.parseDefaulting ChronoField/HOUR_OF_DAY 0)
      (.parseDefaulting ChronoField/MINUTE_OF_HOUR 0)
      (.parseDefaulting ChronoField/SECOND_OF_MINUTE 0)
      (.toFormatter)
      (.withZone zone-utc)))

(defn parse-date [s]
  (-> (.parse formatter s) (ZonedDateTime/from)))

(defn without-time [d]
  (.truncatedTo d ChronoUnit/DAYS))

;; TODO Reset time on days comparison
(defn today
  "The current date."
  []
  (-> (ZonedDateTime/now zone-utc)
      (.withHour 0)
      (.withMinute 0)
      (.withSecond 0)
      (.withNano 0)))

(defn now 
  "The current date and time."
  []
  (ZonedDateTime/now zone-utc))

(defn plus-working-days
  "Add n workdays to Temporal object t."
  [t n]
  (->> (timestream t (if (pos? n) 1 -1) ChronoUnit/DAYS)
       (filter working-day?)
       (drop (Math/abs n))
       (first)))

(defn working-days-between
  "Count the number of workdays between Temporal objects t1 and t2."
  [t1 t2]
  (if (.isAfter t1 t2)
    (working-days-between t2 t1)
    (->> (timestream (with-zero-hours t1) 1 ChronoUnit/DAYS)
         (filter working-day?)
         (take-while (before? (with-zero-hours t2)))
         (count)
         (max 1))))

(defn working-hours-between
  "Count the number of working-hours between Temporal objects t1 and t2."
  [t1 t2]
  (if (.isAfter t1 t2)
    (working-hours-between t2 t1)
    (->> (timestream (with-zero-minutes t1) 1 ChronoUnit/HOURS)
         (filter working-day?)
         (filter working-hour?)
         (take-while (before? (with-zero-minutes t2)))
         (count))))
