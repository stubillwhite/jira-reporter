(ns jira-reporter.jira-client
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]

            [taoensso.timbre :as timbre]
            [jira-reporter.utils :refer [def-]])
  (:import [java.time OffsetDateTime ZonedDateTime ZoneId]))

(timbre/refer-timbre)



(defn get-issue-details
  "Query the JIRA API for the details of issue with the specified ID."
  [{{:keys [username password server project]} :jira} id]
  (debug "Retrieving history for" id)
  (let  [url   (str "https://" server "/rest/api/2/issue/" id)]
    (client/get url {:query-params {:expand "changelog"} :basic-auth [username password]})))

(defn get-jql-query-results
  "Query the JIRA API with the specified JQL."
  [{{:keys [username password server project]} :jira} query]
  (debug (str"Executing query '" query "'"))
  (let [url   (str "https://" server "/rest/api/2/search")]
    (client/get url {:query-params {:jql query :maxResults 500} :basic-auth [username password]})))

;; https://developer.atlassian.com/cloud/jira/software/rest/#introduction
;; https://developer.atlassian.com/cloud/jira/software/rest/#api-board-get

;; curl -G -k -u 'white1:password' 'https://jira.cbsels.com/rest/agile/1.0/issue/NF-55'

;; Get all boards and search
;; Get sprints
;;

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

(defn- paginate [f cursor]
  (let [response (f cursor)]
    (if (:isLast response)
      (:values response)
      (let [{:keys [maxResults startAt isLast]} response]
        (concat (:values response) (paginate f {:maxResults maxResults
                                                :startAt    (+ startAt maxResults)}))))))

(defn- query-params [{{:keys [username password]} :jira}]
  {:query-params {:maxResults 500} :basic-auth [username password]})

;; TODO: Refactor to paginated request and client/request

(defn- with-pagination [f url params & rest]
  (let [merge-params (fn [prms] (update params :query-params #(merge % prms)))
        paginated-f  (fn [crsr] (decode-body (apply f (concat [url (merge-params crsr)] rest))))]
    (paginate paginated-f {})))

(defn get-boards [{{:keys [server]} :jira :as config}]
  (let [url (str "https://" server "/rest/agile/1.0/board")]
    (with-pagination client/get url (query-params config))))

(defn get-sprints-for-board [{{:keys [server project]} :jira :as config} board-id]
  (let [url (str "https://" server "/rest/agile/1.0/board/" board-id "/sprint")]
    (with-pagination client/get url (query-params config))))

(defn get-sprint [{{:keys [server]} :jira :as config} sprint-id]
  (let [url (str "https://" server "/rest/agile/1.0/sprint/" sprint-id)]
    (with-pagination client/get url (query-params config))))

(defn get-sprint [{{:keys [server]} :jira :as config} sprint-id]
  (let [url (str "https://" server "/rest/agile/1.0/sprint/" sprint-id)]
    (with-pagination client/get url (query-params config))))

(defn get-issues-for-sprint [{{:keys [server]} :jira :as config} sprint-id]
  (let [url (str "https://" server "/rest/agile/1.0/sprint/" sprint-id "/issue")]
    (with-pagination client/get url (query-params config))))

(defn- find-first [pred coll]
  (first (filter pred coll)))

(defn get-board-by-name
  ([config name]
   (get-board-by-name config name (get-boards config)))

  ([config name boards]
   (find-first #(= (:name %) name) boards)))

