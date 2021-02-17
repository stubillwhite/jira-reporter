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
            [clojure.string :as string])
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

(def sprint-name  "Sprint 40 Helix")
(def board-name   "CORE Tribe")
(def project-name "SD Personalized Recommender")

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
  (println (reports/generate-buddy-map config (load-cached-issues))))

(defn sprint-from-cache []
  (app/display-report config (reports/generate-sprint-report config (load-cached-issues) (load-cached-sprint))))

(defn daily-from-cache []
  (app/display-report config (reports/generate-daily-report config (load-cached-issues))))

(defn backlog-from-cache []
  (app/display-report config (reports/generate-backlog-report config (load-cached-project))))

;; From real system

(defn burndown []
  (app/-main "--burndown" "--board-name" board-name "--sprint-name" sprint-name))

(defn sprint []
  (app/-main "--sprint-report" "--board-name" board-name "--sprint-name" sprint-name))

(defn daily []
  (app/-main "--daily-report" "--board-name" board-name "--sprint-name" sprint-name))

(defn backlog []
  (app/-main "--backlog-report" "--board-name" board-name "--project-name" project-name))

(defn sprint-backlog []
  (app/display-report {} (reports/generate-backlog-report {:board-name board-name :sprint-name sprint-name})))

;; Historicals

(def historical-sprints
  (for [x (range 30 40)] (format "Sprint %d Helix" x)))

(defn build-metrics [report metric]
  (let [make-keyword (fn [k suffix] (keyword (str (symbol k) suffix "-" metric)))]
    (->> (:rows report)
         (mapcat (fn [{:keys [discipline committed delivered]}]
                   {(make-keyword discipline "-committed") committed
                    (make-keyword discipline "-delivered") delivered}))
         (into {}))))

(defn historical-statistics [sprint-name]
  (let [sprint (jira/get-sprint-named board-name sprint-name)
        issues (jira/get-issues-in-sprint-named board-name sprint-name)]
    (merge 
     (build-metrics (reports/report-discipline-statistics-for-tasks issues) "tasks")
     (build-metrics (reports/report-discipline-statistics-for-points issues) "points")
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
