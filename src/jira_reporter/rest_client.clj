(ns jira-reporter.rest-client
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [taoensso.timbre :as timbre]
            [clojure.string :as string]))

(timbre/refer-timbre)

(defn- decode-value [key value]
  (if (contains? #{:created :dated :startDate :endDate} key)
    (date/parse-date-time value)
    value))

(defn decode-body
  "Returns the JIRA JSON document converted into Clojure data types."
  [x]
  (json/read-str (:body x) :key-fn keyword :value-fn decode-value))

(defn- merge-in [m ks v]
  (update-in m ks #(merge % v)))

(defn- build-request [method url]
  (let [{{:keys [username password batch-size]} :jira} config]
    {:method       method
     :url          url
     :query-params {:maxResults batch-size}
     :basic-auth   [username password]}))

(defn- is-last-page? [response]
  (or (not (contains? response :isLast)) (:isLast response)))

(defn- is-empty-issues? [response]
  (or (not (contains? response :issues)) (empty? (:issues response))))

(defn- redact-credentials [request]
  (update request :basic-auth (fn [x] (if x "REDACTED"))))

(defn- paginated-request
  ([method url f-terminate?]
   (paginated-request (build-request method url) f-terminate?))
  
  ([req f-terminate?]
   (debug "Request: " (redact-credentials req))
   (let [response (decode-body (client/request req))]
     (trace "Response:" response)
     (if (f-terminate? response)
       [response]
       (let [{:keys [maxResults startAt]} response
             new-req (merge-in req [:query-params] {:maxResults maxResults
                                                    :startAt    (+ startAt maxResults)})]
         (lazy-seq (cons response (paginated-request new-req f-terminate?))))))))

(defn- build-api-v1-url [& args]
  (let [{{:keys [server]} :jira} config]
    (str
     (str "https://" server "/rest/agile/1.0")
     (apply str args))))

(defn- build-api-v2-url [& args]
  (let [{{:keys [server]} :jira} config]
    (str
     (str "https://" server "/rest/api/2")
     (apply str args))))

(defn get-board
  "Returns the board with the specified ID."
  [id]
  (->> (paginated-request :get (build-api-v1-url (str "/board/" id)) is-last-page?)))

(defn get-boards
  "Returns a seq of all the boards."
  []
  (->> (paginated-request :get (build-api-v1-url "/board") is-last-page?)
       (mapcat :values)))


(defn get-sprints-for-board
  "Returns a seq of the sprints for the board with the specified ID."
  [board-id]
  (->> (paginated-request :get (build-api-v1-url "/board/" board-id "/sprint") is-last-page?)
       (mapcat :values)))

;; TODO Get sprint by ID

(defn get-issues-for-sprint
  "Returns a seq of the issues for the sprint with the specified ID."
  [sprint-id]
  (let [result (->> (paginated-request (merge-in (build-request :get (build-api-v1-url "/sprint/" sprint-id "/issue"))
                                                 [:query-params]
                                                 {:expand "changelog"})
                                       is-empty-issues?)
                    (mapcat :issues))]
    result))

(defn get-issues-for-backlog
  "Returns a seq of the issues for the backlog of the board with the specified ID."
  [board-id]
  (let [result (->> (paginated-request (merge-in (build-request :get (build-api-v1-url "/board/" board-id "/backlog"))
                                                 [:query-params]
                                                 {:expand "changelog"})
                                       is-empty-issues?)
                    (mapcat :issues))]
    result))

;; See documentation for more details at:
;; https://docs.atlassian.com/software/jira/docs/api/REST/7.6.1/?&_ga=2.213181971.682771901.1564552031-322924290.1564431140#api/2/search-search

(defn- get-issues-for-jql-query [jql]
  (let [epic-link-field    (get-in config [:custom-fields :epic-link])
        story-points-field (get-in config [:custom-fields :story-points])]
    (->> (paginated-request (merge-in (build-request :get (build-api-v2-url "/search"))
                                      [:query-params]
                                      {:jql jql
                                       :expand ["changelog"]
                                       :fields ["key" "created" "parent" "subtasks" "issuetype" "status" "summary"  story-points-field epic-link-field "customfield_10005" "labels" "currentSprint" "closedSprints"]
                                       })
                            is-empty-issues?)
         (mapcat :issues))))

(defn get-issues-for-project
  "Returns a seq of the issues for the project with the specified name."
  [name]
  (get-issues-for-jql-query (str "project=\"" name "\"")))

(defn get-issues-for-epic
  "Returns the issues in the epic with the specified ID."
  [id]
  (get-issues-for-jql-query (str "\"Epic Link\"=\"" id "\"")))

(defn get-issues-for-ids
  "Returns the issues with the specified IDs."
  [ids]
  (let [csv-ids (map (fn [xs] (str "'" (string/join "','" xs) "'"))
                     (partition-all 20 ids))]
    (mapcat (fn [ids] (get-issues-for-jql-query (format "key in (%s)" ids))) csv-ids)))
