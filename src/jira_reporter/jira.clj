(ns jira-reporter.jira
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as spec]
            [com.rpl.specter :refer [ALL collect END select* selected? transform*]]
            [jira-reporter.cache :as cache]
            [jira-reporter.config :refer [config]]
            [jira-reporter.rest-client :as rest-client]
            [jira-reporter.schema :as schema]
            [jira-reporter.utils :refer [def-]]
            [slingshot.slingshot :refer [throw+]]
            [taoensso.timbre :as timbre])
  (:import [java.time OffsetDateTime ZoneId]))

(timbre/refer-timbre)

;; TODO: Story points
;; https://developer.atlassian.com/cloud/jira/platform/rest/v3/#api-api-3-issue-issueIdOrKey-properties-get

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
  (cache/with-cache [:boards name]
    (fn []
      (let [boards (rest-client/get-boards config)]
        (if-let [board (find-first #(= (:name %) name) boards)]
          board
          (throw+ {:type ::board-not-found :boards (map :name boards)}))))))

(defn- get-active-sprint [config board-id]
  (info "Finding the active sprint")
  (let [sprints (rest-client/get-sprints-for-board config board-id)]
    (find-first #(= (:state %) "active") sprints)))

(defn- get-sprint-named [config board-id name]
  (info "Finding the current sprint")
  (let [sprints (rest-client/get-sprints-for-board config board-id)]
    (if-let [sprint (find-first #(= (:name %) name) sprints)]
      sprint
      (throw+ {:type ::sprint-not-found :sprints (map :name sprints)}))))

(defn- extract-issue [issue-json]
  {:id        (get-in issue-json [:key])
   :created   (or (get-in issue-json [:created])
                  (get-in issue-json [:fields :created]))
   :parent-id (get-in issue-json [:fields :parent :key])
   :type      (get-in issue-json [:fields :issuetype :name])
   :status    (get-in issue-json [:fields :status :name])
   :assignee  (get-in issue-json [:fields :assignee :displayName])
   :title     (get-in issue-json [:fields :summary])
   :points    (get-in issue-json [:fields (keyword "customfield_10002")]) ;; TODO Get from schema
   :history   (extract-issue-history issue-json)
   })

(defn- get-issues-for-sprint
  [config sprint-id]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (info "Finding issues in the sprint")
  (->> (rest-client/get-issues-for-sprint config sprint-id)
       (transform* [ALL] extract-issue)))

;; -----------------------------------------------------------------------------
;; Public
;; -----------------------------------------------------------------------------

(defn get-issues-in-current-sprint
  "Get the issues in the current sprint."
  [{{:keys [board]} :jira :as config}]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (let [board  (get-board-named config board)
        sprint (get-active-sprint config (:id board))]
    (get-issues-for-sprint config (:id sprint))))

(defn get-sprint-names
  "Get the names of the sprints."
  [{{:keys [board]} :jira :as config}]
  (let [board   (get-board-named config board)
        sprints (rest-client/get-sprints-for-board config (:id board))]
    (map :name sprints)))

(defn get-board-names
  "Get the names of the boards."
  [config]
  (map :name (rest-client/get-boards config)))

(defn get-issues-in-sprint-named
  "Get the issues in the named sprint."
  [{{:keys [board]} :jira :as config} name]
  (let [board  (get-board-named config board)
        sprint (get-sprint-named config (:id board) name)]
    (get-issues-for-sprint config (:id sprint))))

(defn get-issues-in-project-named
  "Get the issues in the named project."
  [config name]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (info "Finding issues in the project")
  (->> (rest-client/get-issues-for-project config name)
       (transform* [ALL] extract-issue)))

(defn to-do-states       [] (get-in config [:schema :to-do-states]))
(defn in-progress-states [] (get-in config [:schema :in-progress-states]))
(defn blocked-states     [] (get-in config [:schema :blocked-states]))
(defn closed-states      [] (get-in config [:schema :closed-states]))
(defn deployment-states  [] (get-in config [:schema :deployment-states]))
(defn story-types        [] (get-in config [:schema :story-types]))
(defn task-types         [] (get-in config [:schema :task-types]))
(defn bug-types          [] (get-in config [:schema :bug-types]))
(defn gdpr-types         [] (get-in config [:schema :gdpr-types]))
