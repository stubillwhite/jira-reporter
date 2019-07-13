(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint print-table]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clojure.tools.trace :refer [trace-forms trace-ns untrace-ns]]
            [jira-reporter.analysis :as analysis]
            [jira-reporter.config :refer [config]]
            [jira-reporter.jira :as jira]
            [jira-reporter.reports :as reports]
            [jira-reporter.cache :as cache]
            [jira-reporter.app :as app]
            [jira-reporter.rest-client :as rest-client]
            [mount.core :as mount]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre]
            [clojure.pprint :as pprint]
            [jira-reporter.date :as date])
  (:import [java.io DataInputStream DataOutputStream]))

(defn print-methods [x]
  (->> x
       reflect
       :members 
       (filter #(contains? (:flags %) :public))
       (sort-by :name)
       print-table))

(defn write-object [fnam obj]
  (with-open [w (io/output-stream fnam)]
    (nippy/freeze-to-out! (DataOutputStream. w) obj)))

(defn read-object [fnam]
  (with-open [r (io/input-stream fnam)]
    (nippy/thaw-from-in! (DataInputStream. r))))

(defn start []
  (timbre/merge-config! {:appenders {:println {:enabled? true}}})   
  (mount/start))

(defn stop []
  (mount/stop))

(defn reset []
  (stop)
  (refresh :after 'user/start))

;; Helper methods

(defn read-issues []
  (map analysis/add-derived-fields (jira/get-issues-in-current-sprint)))

;; (def issues (read-issues))
;; (write-object "recs-issues.nippy" issues)
;; (def issues (read-object "recs-issues.nippy"))
;; (reports/generate-daily-report config issues)

(defn status-at-date [cutoff-date {:keys [history] :as issue}]
  (if (empty? (:history issue))
    issue
    (reduce
     (fn [acc {:keys [date field to]}] (if (= field "status") (assoc issue :status to) issue))
     (take-while (fn [x] (.isBefore (:date x) cutoff-date)) history))))

(def utc (java.time.ZoneId/of "UTC"))

(defn- utc-date-time
  ([m d]
   (java.time.ZonedDateTime/of 2019 m d 0 0 0 0 utc)))

(defn status-at-date [cutoff-date {:keys [history] :as issue}]
  (if (empty? (:history issue))
    issue
    (reduce
     (fn [acc {:keys [date field to]}] (if (= field "status") (assoc issue :status to) issue))
     (assoc issue :status (-> history first :from))
     (take-while (fn [x] (.isBefore (:date x) cutoff-date)) history))))


;; (reports/tasks-open-and-closed issues)
;; (pprint/pprint
;;  (let [start-date (utc-date-time 6 27)
;;        length     14
;;        ;; timestream (take-while (fn [x] (.isBefore x (jira-reporter.date/today))) (jira-reporter.date/timestream start-date 1 java.time.temporal.ChronoUnit/DAYS))
;;        timestream (take 6 (jira-reporter.date/timestream start-date 1 java.time.temporal.ChronoUnit/DAYS))
;;        ]
;;    (->> timestream
;;         (map (fn [date] (reports/tasks-open-and-closed date
;;                                                       (map (partial status-at-date date) issues)))))))

(defn display-report
  [report]
  (dorun
   (for [{:keys [title columns rows]} report]
     (do
       (println "\n" title)
       (pprint/print-table columns rows)))))


;; (app/-main)
