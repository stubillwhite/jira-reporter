(ns jira-reporter.api
  (:require 
            [clojure.data.json :as json]
            [com.rpl.specter :refer [ALL collect END filterer select* selected?]]
            [jira-reporter.date :as date]
            [jira-reporter.jira-client :as jira-client]
            [jira-reporter.utils :refer [def-]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders])
  (:import [java.time OffsetDateTime ZonedDateTime ZoneId]))

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

(defn- decode-body [x]
  (json/read-str (:body x) :key-fn keyword :value-fn decode-value))

(defn- extract-issue-history [json]
  (let [field-names  [:date :field :from :to ]
        field-values (select* [:changelog :histories ALL
                               (selected? [:items ALL #(= (:field %) "status")])
                               (collect :created)
                               (collect :items ALL :field)
                               (collect :items ALL :fromString)
                               (collect :items ALL :toString) END]
                              json)]
    (->> field-values
         (map flatten)
         (map (fn [x] (apply hash-map (interleave field-names x)))))))

(defn- get-additional-issue-details [config id]
  (let [json (decode-body (jira-client/get-issue-details config id))]
    {:history (extract-issue-history json)}))

(defn- extract-basic-issue-fields [json]
  (let [field-names  [:id :parent-id :type :status :assignee :title]
        field-values (select* [:issues ALL
                               (collect :key)
                               (collect :fields :parent :key)
                               (collect :fields :issuetype :name)
                               (collect :fields :status :name)
                               (collect :fields :assignee :displayName)
                               (collect :fields :summary) END]
                              json)]
    (->> field-values
         (map flatten)
         (map (fn [x] (apply hash-map (interleave field-names x)))))))

(defn- add-additional-issue-details [config issue]
  (merge issue (get-additional-issue-details config (:id issue))))

(defn get-issues-in-current-sprint
  "Get the issues in the current sprint."
  [{{:keys [project]} :jira :as config}]
  (let [query (str "project=" project " and sprint in openSprints()")]
    (->> (jira-client/get-jql-query-results config query)
         (decode-body)
         (extract-basic-issue-fields)
         (pmap (partial add-additional-issue-details config))))) 

(defn get-issues-in-sprint-named
  "Get the issues in the named sprint."
  [{{:keys [project]} :jira :as config} name]
  (let [query (str "project=" project " and sprint = \"" name "\"")]
    (->> (jira-client/get-jql-query-results config query)
         (decode-body)
         (extract-basic-issue-fields)
         (pmap (partial add-additional-issue-details config)))))

(def to-do-states       #{"To Do()" "To Do"})
(def in-progress-states #{"In Progress"})
(def blocked-states     #{"Blocked"})
(def closed-states      #{"Closed - DONE" "Closed"})
(def deployment-states  #{"Deploy to SIT" "Deploy to Prod"})

(def story-types        #{"Story"})
(def task-types         #{"Task" "Sub-task"})
(def bug-types          #{"Bug" "Bug Sub-task"})
(def gdpr-types         #{"GDPR Compliance"})
