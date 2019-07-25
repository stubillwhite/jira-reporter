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

;; Helper methods

(defn read-issues []
  (map analysis/add-derived-fields (jira/get-issues-in-sprint-named "CORE Tribe" "Sprint 2- Hulk")))

(defn read-sprint []
  (jira/get-sprint-named "CORE Tribe" "Sprint 2- Hulk"))

;; (def issues (read-issues))
;; (def sprint (read-sprint))
;; (write-object "recs-issues.nippy" issues)
;; (write-object "recs-sprint.nippy" sprint)
;; (def issues (read-object "recs-issues.nippy"))
;; (def sprint (read-object "recs-sprint.nippy"))
;; (app/display-report (reports/generate-daily-report config issues))
;; (app/display-report (reports/report-burndown (:startDate sprint) (:endDate sprint) issues))

;; (oz/start-server!)

;; (defn plot-burndown-report [report]
;;   {:data {:values plottable-burndown}
;;      :encoding {:x {:field :date :type :temporal}
;;                 :y {:field :open}}
;;    :mark "line"})

;; (defn plot-burndown-report [report]
;;   (let [rows   (map-indexed (fn [idx x] (assoc x :day idx)) (:rows report))
;;         values (for [metric [:open :closed :total]
;;                      row    rows]
;;                  {:metric metric
;;                   :value (get row metric)
;;                   :day   (get row :day)})]
;;     {:data {:values values}
;;      :encoding {:x     {:field :day}
;;                 :y     {:field :value}
;;                 :color {:field :metric :type "nominal"}}
;;      :mark "line"}))

;; (oz/view! (plot-burndown-report burndown))

