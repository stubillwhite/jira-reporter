(ns jira-reporter.reports-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer :all]
            [jira-reporter.reports :refer :all]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time ZonedDateTime ZoneId]))

(def- utc (ZoneId/of "UTC"))

(defn- utc-date-time
  ([m d]
   (ZonedDateTime/of 2000 m d 0 0 0 0 utc))
  ([m d h]
   (ZonedDateTime/of 2000 m d h 0 0 0 utc)))

;; (deftest plus-working-days-given-positive-n-then-skips-weekends
;;   (is (= (utc-date-time 1 4)  (plus-working-days (utc-date-time 1 3) 1)))
;;   (is (= (utc-date-time 1 7)  (plus-working-days (utc-date-time 1 3) 4)))
;;   (is (= (utc-date-time 1 10) (plus-working-days (utc-date-time 1 3) 5))))

;; (deftest foo
;;   (is (= expected (time-in-state))))


(def example-states
  [[:to-do       2]
   [:in-progress 8]
   [:in-test     4]
   [:in-progress 8]
   [:in-test     2]
   [:done        4]])

(defn states [xs]
  (loop [prev-state {:end (utc-date-time 1 1)}
         states     []
         xs         xs]
    (if (empty? xs)
      states
      (let [[id duration] (first xs)
            new-state     {:id    id
                           :start (:end prev-state)
                           :end   (.plusHours (:end prev-state) duration)}]
        (recur new-state (conj states new-state) (rest xs))))))


(defn entry [date from to]
  {:date  date
   :field "status"
   :from  from
   :to    to})

(def example-history
  [(entry (utc-date-time 1 1) :to-do       :in-progress)
   (entry (utc-date-time 1 2) :in-progress :in-test)
   (entry (utc-date-time 1 4) :in-test     :closed)
   ])

(defn truncate-history [history end-date]
  (loop [history   history
         truncated []]
    (if (empty? history)
      truncated
      (let [{:keys [date from to]} (first history)]
        (if (.isAfter date end-date)
          (concat (butlast truncated) [(assoc (last truncated) :date end-date)])
          (recur (rest history) (conj truncated (first history))))))))

;; (clojure.pprint/pprint (truncate-history example-history (utc-date-time 1 1)))


