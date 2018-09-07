(ns jira-reporter.jira-client
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
  (if (contains? #{:created :dated} key)
    (decode-iso-8601-date-time value)
    value))

(defn- decode-body [x]
  (json/read-str (:body x) :key-fn keyword :value-fn decode-value))

(defn- merge-in [m ks v]
  (update-in m ks #(merge % v)))

(defn- build-request [{{:keys [username password]} :jira} method url]
  {:method       method
   :url          url
   :query-params {:maxResults 500}
   :basic-auth   [username password]})

(defn- paginated-request
  ([config method url]
   (paginated-request (build-request config method url)))
  
  ([req]
   (trace "Request: " req)
   (let [response (decode-body (client/request req))]
     (trace "Response:" response)
     (if (or (not (contains? response :isLast)) (:isLast response))
       [response]
       (let [{:keys [maxResults startAt isLast]} response
             new-req (merge-in req [:query-params] {:maxResults maxResults
                                                    :startAt    (+ startAt maxResults)})]
         (lazy-seq (cons response (paginated-request new-req))))))))

(defn- build-url [{{:keys [server]} :jira} & args]
  (str
   (str "https://" server "/rest/agile/1.0")
   (apply str args)))

(defn get-boards [config]
  (->> (paginated-request config :get (build-url config "/board"))
       (mapcat :values)))

(defn get-sprints-for-board [config board-id]
  (->> (paginated-request config :get (build-url config "/board/" board-id "/sprint"))
       (mapcat :values)))

(defn get-sprint [config sprint-id]
  (->> (paginated-request config :get (build-url config "/sprint/" sprint-id))
       (mapcat :values)))

(defn get-issues-for-sprint [config sprint-id]
  (let [result (->> (paginated-request (merge-in (build-request config :get (build-url config "/sprint/" sprint-id "/issue"))
                                                 [:query-params]
                                                 {:expand "changelog"}))
                    (mapcat :issues))]
    result))

