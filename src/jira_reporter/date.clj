(ns jira-reporter.date
  (:require [jira-reporter.utils :refer [def-]])
  (:import [java.time DayOfWeek OffsetDateTime ZonedDateTime ZoneId]
           [java.time.format DateTimeFormatter DateTimeFormatterBuilder]
           [java.time.temporal ChronoField ChronoUnit]))

;; -----------------------------------------------------------------------------
;; Internal
;; -----------------------------------------------------------------------------

(defn- before? [t]
  (fn [x] (.isBefore (.toInstant x) (.toInstant t))))

;; -----------------------------------------------------------------------------
;; Public
;; -----------------------------------------------------------------------------

(def utc
  "UTC timezone."
  (ZoneId/of "UTC"))

(defn truncate-to-hours
  "Returns the date time truncated to hours."
  [dt]
  (.truncatedTo dt ChronoUnit/HOURS))

(defn truncate-to-days
  "Returns the date time truncated to days."
  [dt]
  (.truncatedTo dt ChronoUnit/DAYS))

(defn current-date-time 
  "The current date and time."
  []
  (ZonedDateTime/now utc))

(defn current-date
  "The current date."
  []
  (-> (current-date-time)
      (truncate-to-days)))

(def iso-8601-date-time-formatter
  "A DateTimeFormatter for the ISO 8601 date time format."
  (-> (DateTimeFormatterBuilder.)
      (.append DateTimeFormatter/ISO_LOCAL_DATE_TIME)
      (.optionalStart)
      (.appendOffset "+HH:MM" "+00:00")
      (.optionalEnd)
      (.optionalStart)
      (.appendOffset "+HHMM" "+0000")
      (.optionalEnd)
      (.optionalStart)
      (.appendOffset "+HH" "Z")
      (.optionalEnd)
      (.toFormatter)))

(defn parse-date-time
  "Return a date time parsed from ISO 8601 format string s."
  [s]
  (-> (OffsetDateTime/parse s iso-8601-date-time-formatter)
      (.atZoneSameInstant utc)))

(def- iso-8601-date-formatter
  "A DateTimeFormatter for the ISO 8601 date format."
  (-> (DateTimeFormatterBuilder.)
      (.append DateTimeFormatter/ISO_DATE)
      (.parseDefaulting ChronoField/HOUR_OF_DAY 0)
      (.parseDefaulting ChronoField/MINUTE_OF_HOUR 0)
      (.parseDefaulting ChronoField/SECOND_OF_MINUTE 0)
      (.toFormatter)))

(defn parse-date
  "Return a date parsed from ISO 8601 format string s."
  [s]
  (-> (OffsetDateTime/parse s iso-8601-date-formatter)
      (.atZoneSameInstant utc)))

(defn timestream
  "Returns a lazy sequence of instants starting at time t and increasing
  by n time units each step."
  [t n unit]
  (iterate (fn [x] (.plus x n unit)) t))

(defn working-day?
  "Returns true if date d is a work day, false otherwise."
  [d]
  (not (contains? #{DayOfWeek/SATURDAY DayOfWeek/SUNDAY} (.getDayOfWeek d))))

(defn working-hour?
  "Returns true if date time dt is an hour that is a working hour, false otherwise."
  [dt]
  (let [hour (.getHour dt)]
    (and (working-day? dt) (<= 9 hour 16) (not (= 13 hour)))))

(defn working-days-between
  "Count the number of workdays between Temporal objects t1 and t2."
  [t1 t2]
  (if (.isAfter t1 t2)
    (working-days-between t2 t1)
    (->> (timestream (truncate-to-days t1) 1 ChronoUnit/DAYS)
         (filter working-day?)
         (take-while (before? (truncate-to-days t2)))
         (count)
         (max 1))))

(defn working-hours-between
  "Count the number of working-hours between Temporal objects t1 and t2."
  [t1 t2]
  (if (.isAfter t1 t2)
    (working-hours-between t2 t1)
    (->> (timestream (truncate-to-hours t1) 1 ChronoUnit/HOURS)
         (filter working-day?)
         (filter working-hour?)
         (take-while (before? (truncate-to-hours t2)))
         (count))))
