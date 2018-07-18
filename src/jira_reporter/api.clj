(ns jira-reporter.api
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [com.rpl.specter :refer [ALL collect END filterer select* selected?]]
            [jira-reporter.date :as date]
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

(defn- extract-sprint-status-fields [json]
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

(defn api-get-jql-query
  "Query the JIRA API with the specified JQL."
  [{{:keys [username password server project]} :jira} query]
  (debug "Retrieving sprint status")
  (let [url   (str "https://" server "/rest/api/2/search")]
    (client/get url {:query-params {:jql query} :basic-auth [username password]})))

(defn get-issues-in-current-sprint
  "Get the issues in the current sprint."
  [{{:keys [project]} :jira :as config}]
  (let [query (str "project=" project " and sprint in openSprints()")]
    (-> (api-get-jql-query config query)
        (decode-body)
        (extract-sprint-status-fields))))

(defn- extract-issue-details [json]
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

(defn api-get-issue-details
  "Query the JIRA API for the details of issue with the specified ID."
  [{{:keys [username password server project]} :jira} id]
  (debug "Retrieving history for" id)
  (let  [url   (str "https://" server "/rest/api/2/issue/" id)]
    (client/get url {:query-params {:expand "changelog"} :basic-auth [username password]})))

(defn get-issue-details
  "Get the details for the issue with the specified ID."
  [config id]
  (-> (api-get-issue-details config id)
      (decode-body)
      (extract-issue-details)))

(defn- with-history [config issue]
  (assoc issue :history
         (get-issue-details config (:id issue))))

(defn- with-days-in-progress [issue]
  (assoc issue :days-in-progress
         (if-let [date-started (-> issue :history first :date)]
           (date/workdays-between date-started (date/today))
           0)))

(defn with-issue-details [config issue]
  (-> (with-history config issue)
      (with-days-in-progress)))

(defn issues-in-curent-sprint [config]
  (->> (get-issues-in-current-sprint config)
       (pmap (partial with-issue-details config))))
