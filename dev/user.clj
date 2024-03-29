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
            [jira-reporter.app :as app]
            [jira-reporter.cache :as cache]
            [jira-reporter.config :refer [config]]
            [jira-reporter.jira :as jira]
            [jira-reporter.reports :as reports]
            [jira-reporter.issue-filters :as issue-filters]
            [jira-reporter.rest-client :as rest-client]
            [mount.core :as mount]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre]
            [clojure.pprint :as pprint]
            [jira-reporter.date :as date]
            [clojure.string :as string]
            [jira-reporter.config :as config]
            [jira-reporter.schema :as schema])
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

;; Exploratory methods

(def sprint-name  "Sprint 76")
(def board-name   "CORE Tribe")
(def project-name "SDPR")

(defn read-and-cache-raw-sprint-issues! []
  (let [sprint (jira/get-sprint-named board-name sprint-name)]
    (->> (rest-client/get-issues-for-sprint (:id sprint))
         (write-object "recs-raw-sprint.nippy"))))

(defn read-and-cache-raw-project-issues! []
  (->> (rest-client/get-issues-for-project project-name)
       (write-object "recs-raw-project.nippy")))

(defn read-and-cache-issues! []
  (->> (jira/get-issues-in-sprint-named board-name sprint-name)
       (map analysis/add-derived-fields)
       (write-object "recs-issues.nippy")))

(defn read-and-cache-sprint! []
  (->> (jira/get-sprint-named board-name sprint-name)
       (write-object "recs-sprint.nippy")))

(defn read-and-cache-project! []
  (->> (jira/get-issues-in-project-named project-name)
       (write-object "recs-project.nippy")))

(defn read-and-cache-all! []
  (read-and-cache-raw-sprint-issues!)
  (read-and-cache-raw-project-issues!)
  (read-and-cache-issues!)
  (read-and-cache-sprint!)
  (read-and-cache-project!))

(defn load-cached-raw-sprint-issues []
  (read-object "recs-raw-sprint.nippy"))

(defn load-cached-raw-project-issues []
  (read-object "recs-raw-project.nippy"))

(defn load-cached-issues []
  (read-object "recs-issues.nippy"))

(defn load-cached-sprint []
  (read-object "recs-sprint.nippy"))

(defn load-cached-raw-project []
  (read-object "recs-raw-project.nippy"))

(defn load-cached-project []
  (read-object "recs-project.nippy"))

(defn refresh-cached-data! []
  (read-and-cache-raw-sprint-issues!)
  (read-and-cache-raw-project-issues!)
  (read-and-cache-issues!)
  (read-and-cache-sprint!)
  (read-and-cache-project!))

;; From cache

(defn burndown-from-cache []
  (println (reports/generate-burndown config (load-cached-sprint) (load-cached-issues))))

(defn buddy-map-from-cache []
  (println (reports/generate-buddy-map config (load-cached-sprint) (load-cached-issues))))

(defn sprint-from-cache []
  (app/display-report config (reports/generate-sprint-report config (load-cached-issues) (load-cached-sprint))))

(defn sprint-raw-from-cache []
  (app/display-report config (reports/generate-sprint-report-raw config (load-cached-issues) (load-cached-sprint))))

(defn daily-from-cache []
  (app/display-report config (reports/generate-daily-report config (load-cached-issues))))

(defn backlog-from-cache []
  (app/display-report config (reports/generate-backlog-report config (load-cached-project))))

;; From real system

(defn sprints []
  (app/-main "--list-sprints" "--board-name" board-name))

(defn burndown []
  (app/-main "--burndown" "--board-name" board-name "--sprint-name" sprint-name))

(defn epic-burndown []
  (app/-main "--epic-burndown" "--board-name" board-name "--epic-id" "SDPR-4384"))

(defn buddy-map []
  (app/-main "--buddy-map" "--board-name" board-name "--sprint-name" sprint-name))

(defn sprint []
  (app/-main "--sprint-report" "--board-name" board-name "--sprint-name" sprint-name))

(defn sprint-raw []
  (app/-main "--sprint-report-raw" "--board-name" board-name "--sprint-name" sprint-name "--tsv"))

(defn daily []
  (app/-main "--daily-report" "--board-name" board-name "--sprint-name" sprint-name))

(defn backlog []
  (app/-main "--backlog-report" "--board-name" board-name "--project-name" project-name))

(defn sprint-backlog []
  (app/display-report {} (reports/generate-backlog-report {:board-name board-name :sprint-name sprint-name})))

;; Historicals

(def historical-sprints
  (for [x (range 50 56)] (format "Sprint %d Orion" x)))

(defn build-metrics [report metric]
  (let [make-keyword (fn [k suffix] (keyword (str (symbol k) suffix "-" metric)))]
    (->> (:rows report)
         (mapcat (fn [{:keys [discipline committed delivered]}]
                   {(make-keyword discipline "-committed") committed
                    (make-keyword discipline "-delivered") delivered}))
         (into {}))))

(defn historical-statistics [sprint-name]
  (let [sprint (jira/get-sprint-named board-name sprint-name)
        issues (reports/raw-issues (jira/get-issues-in-sprint-named board-name sprint-name) sprint)]
    (merge 
     (build-metrics (reports/report-user-level-task-statistics     issues) "tasks")
     (build-metrics (reports/report-business-level-task-statistics issues) "points")
     {:sprint sprint-name})))

(defn display-all-historical-stats []
  (let [columns (cons :sprint
                      (for [metric ["points" "tasks"]
                            state  ["committed" "delivered"]
                            role   ["engineering" "data-science" "infrastructure"]]
                        (keyword (str role "-" state "-" metric))))
        rows     (map historical-statistics historical-sprints)]
    (print-table columns rows)))

;; (display-all-historical-stats)

(defn min-by [f coll]
  (when (seq coll)
    (apply min-key f coll)))

(defn max-by [f coll]
  (when (seq coll)
    (apply max-key f coll)))

(defn white-test []
  (let [id             "SDPR-4384"
        epic           (first (jira/get-issues-with-ids [id]))
        issues         (jira/get-issues-in-epic-with-id id)
        to-in-progress (fn [{:keys [field to]}] (and (= field "status")
                                                    (or (contains? (jira/in-progress-states) to)
                                                        (contains? (jira/closed-states) to))))
        to-closed      (fn [{:keys [field to]}] (and (= field "status") (contains? (jira/closed-states) to)))
        date-millis    (fn [x] (-> x (:date) (.toInstant) (.toEpochMilli)))
        start-date     (->> issues (mapcat :history) (filter to-in-progress) (min-by date-millis))
        end-date       (->> issues (mapcat :history) (filter to-closed)      (max-by date-millis))
        points         (->> issues (map :points) (filter identity) (apply +))]
    (println (str "Started: " start-date))
    (println (str "Closed:  " end-date))
    (println (str "Points:  " points))))

;; (reports/buddy-pairings (load-cached-issues))

;; (buddy-map-from-cache)

;; (def issues (load-cached-issues))
;; 
;; (->> issues
;;      (map :id))
;; 
;; (def issue (->> issues
;;                 (filter (fn [x] (= (:id x) "SDPR-5526")))
;;                 (first)))
;; 
;; (pprint (:history issue))
;; 
;; 
;; 
;; (->> issue
;;      (issue-filters/issue-at-date (date/parse-date "2021-09-26Z"))
;;      ((fn [x] (select-keys x [:history :status])))
;;      (pprint))
;; 

;; (defn- get-issues [sprint-name]
;;   (let [sprint (jira/get-sprint-named board-name sprint-name)]
;;     (rest-client/get-issues-for-sprint (:id sprint))))
;; 
;; (let [sprints     ["Sprint 63"]
;;       last-sprint (jira/get-sprint-named board-name (last sprints))
;;       issues      (mapcat get-issues sprints)
;;       deduped     (for [[k vs] (group-by :id issues)] (first vs))]
;;   (pprint (reports/generate-buddy-map nil last-sprint issues)))

