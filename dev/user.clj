(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require [clojure.edn :as edn]
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
            [jira-reporter.rest-client :as rest-client]
            [mount.core :as mount]
            [taoensso.timbre :as timbre]))

(defn print-methods [x]
  (->> x
       reflect
       :members 
       (filter #(contains? (:flags %) :public))
       (sort-by :name)
       print-table))

(defn write-object [fnam obj]
  (spit fnam (prn-str obj)))

(defn read-object [fnam]
  (edn/read-string (slurp fnam)))

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
  (map analysis/add-derived-fields (jira/get-issues-in-current-sprint config)))

(defn read-raw-issues []
  (rest-client/get-issues-for-sprint config 6456))

;; (def issues (read-issues))
;; (pprint (first issues))
;; (reports/generate-daily-report config issues)
