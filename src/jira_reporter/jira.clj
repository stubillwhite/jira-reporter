(ns jira-reporter.jira
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as spec]
            [com.rpl.specter :refer [ALL collect END select* selected? transform*]]
            [jira-reporter.rest-client :as rest-client]
            [jira-reporter.schema :as schema]
            [jira-reporter.utils :refer [def-]]
            [taoensso.timbre :as timbre]
            [jira-reporter.config :refer [config]])
  (:import [java.time OffsetDateTime ZoneId]))

(timbre/refer-timbre)

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
  (let [boards (rest-client/get-boards config)]
    (find-first #(= (:name %) name) boards)))

(defn- get-active-sprint [config board-id]
  (info "Finding the active sprint")
  (let [sprints (rest-client/get-sprints-for-board config board-id)]
    (find-first #(= (:state %) "active") sprints)))

(defn- get-sprint-named [config board-id name]
  (info "Finding the current sprint")
  (let [sprints (rest-client/get-sprints-for-board config board-id)]
    (find-first #(= (:name %) name) sprints)))

(defn- extract-issue [issue-json]
  {:id        (get-in issue-json [:key])
   :created   (or (get-in issue-json [:created])
                  (get-in issue-json [:fields :created]))
   :parent-id (get-in issue-json [:fields :parent :id])
   :type      (get-in issue-json [:fields :issuetype :name])
   :status    (get-in issue-json [:fields :status :name])
   :assignee  (get-in issue-json [:fields :assignee :displayName])
   :title     (get-in issue-json [:fields :summary])
   :history   (extract-issue-history issue-json)
   })

(defn- get-issues-for-sprint
  [config sprint-id]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (info "Finding issues in the sprint")
  (->> (rest-client/get-issues-for-sprint config sprint-id)
       (transform* [ALL] extract-issue)))

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

(defn to-do-states       [] (get-in config [:schema :to-do-states]))
(defn in-progress-states [] (get-in config [:schema :in-progress-states]))
(defn blocked-states     [] (get-in config [:schema :blocked-states]))
(defn closed-states      [] (get-in config [:schema :closed-states]))
(defn deployment-states  [] (get-in config [:schema :deployment-states]))
(defn story-types        [] (get-in config [:schema :story-types]))
(defn task-types         [] (get-in config [:schema :task-types]))
(defn bug-types          [] (get-in config [:schema :bug-types]))
(defn gdpr-types         [] (get-in config [:schema :gdpr-types]))
