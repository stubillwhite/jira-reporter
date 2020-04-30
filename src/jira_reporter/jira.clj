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

(declare epic-link-field)
(declare story-points-field)

;; (defn- extract-issue-history [json]
;;   (let [field-names  [:date :field :from :to ]
;;         field-values (select* [ALL
;;                                (selected? [:items ALL #(= (:field %) "status")])
;;                                (collect :created)
;;                                (collect :items ALL :field)
;;                                (collect :items ALL :fromString)
;;                                (collect :items ALL :toString) END]
;;                               (get-in json [:changelog :histories]))]
;;     (->> field-values
;;          (map flatten)
;;          (map (fn [x] (apply hash-map (interleave field-names x)))))))

(defn- extract-issue-history [json]
  (->> (get-in json [:changelog :histories])
       (mapcat (fn [x] (map #(assoc % :date (:created x)) (:items x))))
       (filter (fn [x] (= (:field x) "status")))
       (map (fn [{:keys [date field fromString toString]}]
              {:date  date
               :field field
               :from  fromString
               :to    toString}))))

(defn- add-additional-issue-details [issue]
  (assoc issue :history (extract-issue-history issue)))

(defn- find-first [pred coll]
  (first (filter pred coll)))

(defn- get-board-named [board-name]
  (cache/with-cache [:boards board-name]
    (fn []
      (info (str "Failed to find board \"" board-name "\" in cache"))
      (let [boards (rest-client/get-boards)]
        (if-let [board (find-first #(= (:name %) board-name) boards)]
          board
          (throw+ {:type ::board-not-found :boards (map :name boards)}))))))

(defn- extract-sprint [sprint-json]
  (when sprint-json
    {:id         (str (get-in sprint-json [:id]))
     :name       (get-in sprint-json [:name])
     :state      (get-in sprint-json [:state])
     :start-date (get-in sprint-json [:startDate])
     :end-date   (get-in sprint-json [:endDate])}))

(defn- extract-issue [issue-json]
  {:id             (get-in issue-json [:key])
   :created        (or (get-in issue-json [:created])
                       (get-in issue-json [:fields :created]))
   :parent-id      (get-in issue-json [:fields :parent :key])
   :subtask-ids    (->> (get-in issue-json [:fields :subtasks]) (map :key))
   :type           (get-in issue-json [:fields :issuetype :name])
   :status         (get-in issue-json [:fields :status :name])
   :assignee       (get-in issue-json [:fields :assignee :displayName])
   :title          (get-in issue-json [:fields :summary])
   :points         (get-in issue-json [:fields (keyword (story-points-field))])
   :epic           (get-in issue-json [:fields (keyword (epic-link-field))])
   :labels         (get-in issue-json [:fields :labels])
   :current-sprint (extract-sprint (get-in issue-json [:fields :sprint]))
   :closed-sprints (->> (get-in issue-json [:fields :closedSprints]) (map extract-sprint))
   :history        (extract-issue-history issue-json)})

(defn- get-issues-for-sprint
  [sprint-id]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (->> (rest-client/get-issues-for-sprint sprint-id)
       (transform* [ALL] extract-issue)))

;; -----------------------------------------------------------------------------
;; Public
;; -----------------------------------------------------------------------------

(defn get-sprints
  "Get the sprints."
  [board-name]
  {:post [(spec/assert (spec/coll-of ::schema/sprint) %)]}
  (info (str "Getting sprints in board '" board-name "'"))
  (let [board   (get-board-named board-name)
        sprints (rest-client/get-sprints-for-board (:id board))]
    (map extract-sprint sprints)))

(defn get-sprint-named
  "Get the named sprint."
  [board-name sprint-name]
  {:post [(spec/assert ::schema/sprint %)]}
  (info (str "Getting sprint named '" sprint-name "' in board '" board-name "'"))
  (let [board   (get-board-named board-name)
        sprints (rest-client/get-sprints-for-board (:id board))]
    (if-let [sprint-json (find-first #(= (:name %) sprint-name) sprints)]
      (extract-sprint sprint-json)
      (throw+ {:type ::sprint-not-found :sprints (map :name sprints)}))))

(defn get-issues-in-sprint-named
  "Get the issues in the named sprint."
  [board-name sprint-name]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (let [sprint (get-sprint-named board-name sprint-name)]
    (info (str "Getting the issues in sprint '" sprint-name "' of board '" board-name "'"))
    (get-issues-for-sprint (:id sprint))))

(defn get-issues-in-backlog
  "Get issues in the backlog."
  [board-name]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (let [board (get-board-named board-name)]
    (info (str "Getting the issues in the backlog of board '" board-name "'"))
    (->> (rest-client/get-issues-for-backlog (:id board))
         (transform* [ALL] extract-issue))))

(defn get-issues-in-project-named
  "Get the issues in the named project."
  [name]
  {:post [(spec/assert (spec/coll-of ::schema/issue) %)]}
  (info (str "Finding issues in the the project '" name "'"))
  (->> (rest-client/get-issues-for-project name)
       (transform* [ALL] extract-issue)))

(defn to-do-states       [] (get-in config [:schema :to-do-states]))
(defn in-progress-states [] (get-in config [:schema :in-progress-states]))
(defn blocked-states     [] (get-in config [:schema :blocked-states]))
(defn closed-states      [] (get-in config [:schema :closed-states]))
(defn deployment-states  [] (get-in config [:schema :deployment-states]))
(defn epic-types         [] (get-in config [:schema :epic-types]))
(defn story-types        [] (get-in config [:schema :story-types]))
(defn task-types         [] (get-in config [:schema :task-types]))
(defn subtask-types      [] (get-in config [:schema :subtask-types]))
(defn bug-types          [] (get-in config [:schema :bug-types]))
(defn gdpr-types         [] (get-in config [:schema :gdpr-types]))
(defn epic-types         [] (get-in config [:schema :epic-types]))

(defn epic-link-field    [] (get-in config [:custom-fields :epic-link]))
(defn story-points-field [] (get-in config [:custom-fields :story-points]))

