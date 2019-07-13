(ns jira-reporter.rest-client
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [taoensso.timbre :as timbre]
            [jira-reporter.utils :refer [def-]])
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
  (if (contains? #{:created :dated :startDate :endDate} key)
    (decode-iso-8601-date-time value)
    value))

(defn decode-body
  "Returns the JIRA JSON document converted into Clojure data types."
  [x]
  (json/read-str (:body x) :key-fn keyword :value-fn decode-value))

(defn- merge-in [m ks v]
  (update-in m ks #(merge % v)))

(defn- build-request [{{:keys [username password batch-size]} :jira} method url]
  {:method       method
   :url          url
   :query-params {:maxResults batch-size}
   :basic-auth   [username password]})

(defn- is-last-page? [response]
  (or (not (contains? response :isLast)) (:isLast response)))

(defn- is-empty-issues? [response]
  (or (not (contains? response :issues)) (empty? (:issues response))))

(defn- paginated-request
  ([config method url f-terminate?]
   (paginated-request (build-request config method url) f-terminate?))
  
  ([req f-terminate?]
   (debug "Request: " req)
   (let [response (decode-body (client/request req))]
     (trace "Response:" response)
     (if (f-terminate? response)
       [response]
       (let [{:keys [maxResults startAt isLast]} response
             new-req (merge-in req [:query-params] {:maxResults maxResults
                                                    :startAt    (+ startAt maxResults)})]
         (lazy-seq (cons response (paginated-request new-req f-terminate?))))))))

(defn- build-api-v1-url [{{:keys [server]} :jira} & args]
  (str
   (str "https://" server "/rest/agile/1.0")
   (apply str args)))

(defn- build-api-v2-url [{{:keys [server]} :jira} & args]
  (str
   (str "https://" server "/rest/api/2")
   (apply str args)))

(defn get-board
  "Returns the board with the specified ID."
  [config id]
  (->> (paginated-request config :get (build-api-v1-url config (str "/board/" id)) is-last-page?)))

(defn get-boards
  "Returns a seq of all the boards."
  [config]
  (->> (paginated-request config :get (build-api-v1-url config "/board") is-last-page?)
       (mapcat :values)))

(defn get-sprints-for-board
  "Returns a seq of the sprints for the board with the specified ID."
  [config board-id]
  (->> (paginated-request config :get (build-api-v1-url config "/board/" board-id "/sprint") is-last-page?)
       (mapcat :values)))

;; TODO Get sprint by ID

(defn get-issues-for-sprint
  "Returns a seq of the issues for the sprint with the specified ID."
  [config sprint-id]
  (let [result (->> (paginated-request (merge-in (build-request config :get (build-api-v1-url config "/sprint/" sprint-id "/issue"))
                                                 [:query-params]
                                                 {:expand "changelog"})
                                       is-empty-issues?)
                    (mapcat :issues))]
    result))

(defn get-issues-for-project
  "Returns a seq of the issues for the project with the specified ID."
  [config project-id]
  (let [result (->> (paginated-request (merge-in (build-request config :get (build-api-v2-url config "/search"))
                                                 [:query-params]
                                                 {:jql    (str "project=" project-id)
                                                  :expand "changelog"})
                                       is-empty-issues?)
                    (mapcat :issues))]
    result))

