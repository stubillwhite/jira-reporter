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

(defn- get-board-named [board-name]
  (info (str "Getting board named '" board-name "'"))
  (cache/with-cache [:boards board-name]
    (fn []
      (info (str "Failed to find board \"" board-name "\" in cache"))
      (let [boards (rest-client/get-boards config)]
        (if-let [board (find-first #(= (:name %) board-name) boards)]
          board
          (throw+ {:type ::board-not-found :boards (map :name boards)}))))))

(defn- extract-issue [issue-json]
  {:id          (get-in issue-json [:key])
   :created     (or (get-in issue-json [:created])
                    (get-in issue-json [:fields :created]))
   :parent-id   (get-in issue-json [:fields :parent :key])
   :subtask-ids (->> (get-in issue-json [:fields :subtasks]) (map :key))
   :type        (get-in issue-json [:fields :issuetype :name])
   :status      (get-in issue-json [:fields :status :name])
   :assignee    (get-in issue-json [:fields :assignee :displayName])
   :title       (get-in issue-json [:fields :summary])
   :points      (get-in issue-json [:fields (keyword "customfield_10002")]) ;; TODO Get from schema
   :history     (extract-issue-history issue-json)})

(defn- get-issues-for-sprint
  [sprint-id]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (->> (rest-client/get-issues-for-sprint config sprint-id)
       (transform* [ALL] extract-issue)))

;; -----------------------------------------------------------------------------
;; Public
;; -----------------------------------------------------------------------------

(defn get-active-sprint 
  "Get the active sprint."
  ([board-name]
   (let [board   (get-board-named board-name)
         sprints (rest-client/get-sprints-for-board config (:id board))]
     (if-let [sprint (find-first #(= (:state %) "active") sprints)]
       sprint
       (throw+ {:type ::active-sprint-not-found :sprints (map :name sprints)})))))

(defn get-sprint-named
  "Get the named sprint."
  ([board-name sprint-name]
   (let [board   (get-board-named board-name)
         sprints (rest-client/get-sprints-for-board config (:id board))]
     (if-let [sprint (find-first #(= (:name %) sprint-name) sprints)]
       sprint
       (throw+ {:type ::sprint-not-found :sprints (map :name sprints)})))))

(defn get-issues-in-current-sprint
  "Get the issues in the current sprint."
  [board-name]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (let [sprint (get-active-sprint board-name)]
    (info (str "Getting the issues in the current sprint of board '" board-name "'"))
    (get-issues-for-sprint (:id sprint))))

(defn get-sprint-names
  "Get the names of the sprints."
  [board-name]
  (let [board   (get-board-named board-name)
        sprints (rest-client/get-sprints-for-board config (:id board))]
    (map :name sprints)))

(defn get-board-names
  "Get the names of the boards."
  []
  (map :name (rest-client/get-boards config)))

(defn get-issues-in-sprint-named
  "Get the issues in the named sprint."
  [board-name sprint-name]
  (let [sprint (get-sprint-named board-name sprint-name)]
    (info (str "Getting the issues in sprint '" sprint-name "' of board '" board-name "'"))
    (get-issues-for-sprint (:id sprint))))

(defn get-issues-in-project-named
  "Get the issues in the named project."
  [name]
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
