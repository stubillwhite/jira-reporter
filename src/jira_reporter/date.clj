(ns jira-reporter.date
  (:import [java.time DayOfWeek ZonedDateTime ZoneId]
           java.time.temporal.ChronoUnit))

(defn- timestream [t n unit]
  (iterate (fn [x] (.plus x n unit)) t))

(defn- weekday? [x]
  (not (contains? #{DayOfWeek/SATURDAY DayOfWeek/SUNDAY} (.getDayOfWeek x))))

(defn- before? [t]
  (fn [x] (.isBefore (.toInstant x) (.toInstant t))))

(defn today 
  "The current date."
  []
  (-> (ZonedDateTime/now (ZoneId/of "UTC"))
      (.withHour 0)
      (.withMinute 0)
      (.withSecond 0)
      (.withSecond 0)
      (.withNano 0)))

(defn plus-workdays
  "Add n workdays to Temporal object t."
  [t n]
  (->> (timestream t (if (pos? n) 1 -1) ChronoUnit/DAYS)
       (filter weekday?)
       (drop (Math/abs n))
       (first)))

(defn workdays-between
  "Count the number of workdays between Temporal objects t1 and t2."
  [t1 t2]
  (if (.isAfter t1 t2)
    (workdays-between t2 t1)
    (->> (timestream t1 1 ChronoUnit/DAYS)
         (filter weekday?)
         (take-while (before? t2))
         (count))))
