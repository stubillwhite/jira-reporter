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
            [jira-reporter.rest-client :as rest-client]
            [mount.core :as mount]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre]
            [clojure.pprint :as pprint])
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
  (map analysis/add-derived-fields (jira/get-issues-in-current-sprint config)))

;; (def issues (read-issues))
;; (write-object "recs-issues.nippy" issues)
;; (def issues (read-object "recs-issues.nippy"))
;; (reports/generate-daily-report config issues)
