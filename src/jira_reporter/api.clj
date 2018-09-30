(ns jira-reporter.api
  (:require [clojure.data.json :as json]
            [com.rpl.specter :refer [ALL collect END select* selected? transform*]]
            [jira-reporter.jira-client :as jira-client]
            [jira-reporter.utils :refer [def-]]
            [taoensso.timbre :as timbre])
  (:import [java.time OffsetDateTime ZoneId]))

(timbre/refer-timbre)

(def- iso-8601-date-time-formatter
  (-> (java.time.format.DateTimeFormatterBuilder.)
      (.append java.time.format.DateTimeFormatter/ISO_LOCAL_DATE_TIME)
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

(defn- decode-iso-8601-date-time [s]
  (-> (OffsetDateTime/parse s iso-8601-date-time-formatter)
      (.atZoneSameInstant (ZoneId/of "UTC"))))

(defn- decode-value [key value]
  (if (contains? #{:created :dated} key)
    (decode-iso-8601-date-time value)
    value))

(defn decode-body [x]
  (json/read-str (:body x) :key-fn keyword :value-fn decode-value))

(defn- extract-issue-history [json]
  (let [field-names  [:date :field :from :to ]
        field-values (select* [ALL
                               (selected? [:items ALL #(= (:field %) "status")])
                               (collect :created)
                               (collect :items ALL :field)
                               (collect :items ALL :fromString)
                               (collect :items ALL :toString) END]
                              (get-in json [:changelog :histories]))]
    (->> field-values
         (map flatten)
         (map (fn [x] (apply hash-map (interleave field-names x)))))))

(defn- add-additional-issue-details [issue]
  (assoc issue :history (extract-issue-history issue)))

(defn- find-first [pred coll]
  (first (filter pred coll)))

(defn- get-board-named [config name]
  (info "Finding board named" name)
  (let [boards (jira-client/get-boards config)]
    (find-first #(= (:name %) name) boards)))

(defn- get-active-sprint [config board-id]
  (info "Finding the active sprint")
  (let [sprints (jira-client/get-sprints-for-board config board-id)]
    (find-first #(= (:state %) "active") sprints)))

(defn- get-sprint-named [config board-id name]
  (info "Finding the current sprint")
  (let [sprints (jira-client/get-sprints-for-board config board-id)]
    (find-first #(= (:name %) name) sprints)))

(defn extract-issue [issue-json]
  {:id        (get-in issue-json [:key])
   :parent-id (get-in issue-json [:fields :parent])
   :type      (get-in issue-json [:fields :issuetype :name])
   :status    (get-in issue-json [:fields :status :name])
   :assignee  (get-in issue-json [:fields :assignee :displayName])
   :title     (get-in issue-json [:fields :summary])
   :history   (extract-issue-history issue-json)
   })

(defn- get-issues-for-sprint [config sprint-id]
  (info "Finding issues in the sprint")
  (->> (jira-client/get-issues-for-sprint config sprint-id)
       (transform* [ALL] extract-issue)))

;; (->> (deref jira-reporter.jira-client/debug-data) (transform* [ALL] extract-issue))
;; (->> (deref jira-reporter.jira-client/debug-data) (first) (clojure.pprint/pprint))

(defn get-issues-in-current-sprint
  "Get the issues in the current sprint."
  [{{:keys [board]} :jira :as config}]
  (let [board  (get-board-named config board)
        sprint (get-active-sprint config (:id board))]
    (get-issues-for-sprint config (:id sprint))))

(defn get-issues-in-sprint-named
  "Get the issues in the named sprint."
  [{{:keys [board]} :jira :as config} name]
  (let [board  (get-board-named config board)
        sprint (get-sprint-named config (:id board) name)]
    (get-issues-for-sprint config (:id sprint))))

(def to-do-states       #{"To Do()" "To Do"})
(def in-progress-states #{"In Progress"})
(def blocked-states     #{"Blocked"})
(def closed-states      #{"Closed - DONE" "Closed"})
(def deployment-states  #{"Deploy to SIT" "Deploy to Prod"})

(def story-types        #{"Story"})
(def task-types         #{"Task" "Sub-task"})
(def bug-types          #{"Bug" "Bug Sub-task"})
(def gdpr-types         #{"GDPR Compliance"})
