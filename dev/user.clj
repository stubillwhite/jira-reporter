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
            [oz.core :as oz])
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

(defn read-and-cache-issues! []
  (let [board  "CORE Tribe"
        sprint "Sprint 10 Hulk"]
    (->> (jira/get-issues-in-sprint-named board sprint)
         (map analysis/add-derived-fields)
         (write-object "recs-issues.nippy"))))

(defn read-and-cache-sprint! []
  (let [board  "CORE Tribe"
        sprint "Sprint 10 Hulk"]
   (->> (jira/get-sprint-named board sprint)
        (write-object "recs-sprint.nippy"))))

(defn load-cached-issues []
  (read-object "recs-issues.nippy"))

(defn load-cached-sprint []
  (read-object "recs-sprint.nippy"))

(defn- display-table
  [report]
  (dorun
   (for [{:keys [title columns rows]} report]
     (do
       (println title)
       (if (empty? rows)
         (println "\nNone")
         (pprint/print-table columns rows))
       (println)))))

(def sprint-name "Sprint 9 Hulk")
(def board-name "CORE Tribe")

(defn burndown []
  (app/-main "--burndown" "--board-name" board-name "--sprint-name" sprint-name))

(defn sprint []
  (app/-main "--sprint-report" "--board-name" board-name "--sprint-name" sprint-name))

(defn daily []
  (app/-main "--daily-report" "--board-name" board-name "--sprint-name" sprint-name))

(defn backlog []
  (app/-main "--backlog-report" "SD Personalized Recommender"))

(defn sprint-backlog []
  (app/display-report {} (reports/generate-backlog-sprint-report {:board-name board-name :sprint-name sprint-name})))
