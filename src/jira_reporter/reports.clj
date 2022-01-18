(ns jira-reporter.reports
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [jira-reporter.analysis :as analysis]
            [jira-reporter.config :refer [config]]
            [jira-reporter.date :as date]
            [jira-reporter.issue-filters :refer :all]
            [jira-reporter.jira :as jira]
            [jira-reporter.utils :refer [any-pred def- map-vals]]
            [taoensso.timbre :as timbre])
  (:import java.time.format.DateTimeFormatter
           java.time.temporal.ChronoUnit))

(timbre/refer-timbre)

;; TODO: Move to filters or dates
(defn- before-or-equal? [a b]
  (let [date-a (date/truncate-to-days a)
        date-b (date/truncate-to-days b)]
    (or (= date-a date-b) (.isBefore date-a date-b))))

;; -----------------------------------------------------------------------------
;; Generic functions
;; -----------------------------------------------------------------------------

(defn- issues-in-sprint-named [board-name sprint-name]
  (map analysis/add-derived-fields (jira/get-issues-in-sprint-named board-name sprint-name)))

;; TODO: Should be on the 'add metadata bit'
(defn- add-time-in-state [t]
  (let [days-in-state (fn [x] (int (/ (get-in t [:time-in-state x] 0) 7)))]
    (-> t
        (assoc :time-in-todo       (days-in-state :todo))
        (assoc :time-in-progress   (days-in-state :in-progress))
        (assoc :time-in-blocked    (days-in-state :blocked))
        (assoc :time-in-deployment (days-in-state :deployment)))))

(defn- add-metadata [sprint issue]
  (let [buddiable?        (every-pred user-level-task? (complement personal-development?))
        work-started?     (any-pred in-progress? closed?)
        discipline        (fn [x] (cond
                                   (engineering? x)    :engineering
                                   (data-science? x)   :data-science
                                   (infrastructure? x) :infrastructure
                                   :else               :other))
        raised-in-sprint? (fn [x] (and (before-or-equal? (:start-date sprint) (:created issue))
                                      (before-or-equal? (:created issue)     (:end-date sprint))))]
    (assoc issue
           :user-level-task?      (user-level-task? issue)
           :business-deliverable? (business-deliverable? issue)
           :discipline            (discipline issue)
           :work-started?         (work-started? issue)
           :work-complete?        (closed? issue)
           :buddiable?            (buddiable? issue)
           :buddied?              (buddied? issue)
           :raised-in-sprint?     (raised-in-sprint? issue))))

(defn raw-issues [issues sprint]
  (let [open-in-sprint? (fn [x] (open? (issue-at-date (:start-date sprint) x)))]
    (->> issues
         (filter open-in-sprint?)
         (map (partial issue-at-date (:end-date sprint)))
         (filter identity)
         (map (partial add-metadata sprint)))))

(defn- buddies-to-str [x]
  (assoc x :buddies (string/join ", " (:buddies x))))

;; -----------------------------------------------------------------------------
;; Sprint report raw
;; -----------------------------------------------------------------------------

(defn generate-sprint-report-raw
  "Generate the report of raw sprint issues."
  ([options]
   (let [{:keys [board-name sprint-name]} options
         sprint                           (jira/get-sprint-named board-name sprint-name)
         issues                           (jira/get-issues-in-sprint-named board-name sprint-name)]
     (generate-sprint-report-raw options issues sprint)))

  ([options issues sprint]
   [{:title   "Raw issues"
     :columns [:id :type :title :assignee :status :buddies :user-level-task? :business-deliverable? :discipline :work-started? :work-complete? :buddiable? :buddied? :raised-in-sprint?]
     :rows    (->> (raw-issues issues sprint)
                   (map buddies-to-str))}]))

;; -----------------------------------------------------------------------------
;; Sprint names
;; -----------------------------------------------------------------------------

(defn generate-sprint-names-report
  "Generate a report of the sprint names for a board."
  [options]
  [{:title   "Sprint names"
    :columns [:name :goal]
    :rows    (for [sprint (jira/get-sprints (:board-name options))] {:name (:name sprint) :goal (:goal sprint)})}])

;; -----------------------------------------------------------------------------
;; Daily report
;; -----------------------------------------------------------------------------

(defn report-issues-blocked [issues]
  {:title   "Issues currently blocked"
   :columns [:id :type :title :assignee :buddies]
   :rows    (->> issues
                 (filter (every-pred blocked?))
                 (map buddies-to-str))})

(defn report-issues-started [issues]
  {:title   "Issues started yesterday"
   :columns [:id :type :title :assignee :buddies]
   :rows    (->> issues
                 (filter (every-pred changed-state-in-the-last-day? in-progress?))
                 (map buddies-to-str))})

(defn report-issues-in-progress [issues]
  {:title   "Issues still in progress"
   :columns [:id :type :title :assignee :buddies]
   :rows    (->> issues
                 (filter (every-pred (complement changed-state-in-the-last-day?) in-progress? user-level-task?))
                 (map buddies-to-str))})

(defn report-issues-ready-for-release [issues]
  {:title   "Issues awaiting release"
   :columns [:id :type :title :assignee :buddies]
   :rows    (->> issues
                 (filter (every-pred awaiting-deployment?))
                 (map buddies-to-str))})

(defn report-issues-closed [issues]
  {:title   "Issues closed yesterday"
   :columns [:id :type :title :assignee :buddies]
   :rows    (->> issues
                 (filter (every-pred changed-state-in-the-last-day? closed?))
                 (map buddies-to-str))})

(defn report-issues-needing-sizing [issues]
  {:title   "Issues needing sizing"
   :columns [:id :type :title :assignee]
   :rows    (filter needs-size? issues)})

(defn- build-buddying-commitments [issues]
  (let [add-commitments (fn [acc id buddies] (reduce (fn [acc buddy] (update acc buddy (fnil conj []) id)) acc buddies))]
    (reduce (fn [acc {:keys [id buddies]}] (add-commitments acc id buddies))
            {}
            issues)))

(defn report-buddying-commitments [issues]
  (let [commitments (build-buddying-commitments issues)]
    {:title   "Buddying commitments"
     :columns [:user :count :issues]
     :rows    (sort-by :user
                       (for [[user buddy-issues] commitments]
                         {:user   user
                          :count  (count buddy-issues)
                          :issues (string/join ", " (sort buddy-issues))}))}))

(defn report-issues-needing-buddies [issues]
  (let [needs-buddy? (every-pred user-level-task? (any-pred in-progress?) (complement personal-development?) (complement buddied?))]
    {:title   "Issues needing buddies"
     :columns [:id :type :title :assignee]
     :rows    (filter needs-buddy? issues)}))

(defn report-issues-needing-triage [issues]
  {:title   "Issues needing triage"
   :columns [:id :type :title :assignee]
   :rows    (filter needs-triage? issues)})

(defn report-issues-needing-jira-clean-up [issues]
  (let [problem-types        {:missing-discipline-allocation unallocated?}
        add-attention-needed (fn [x] (assoc x :problems (string/join " " (for [[k v] problem-types :when (v x)] k))))
        needs-attention?     (fn [x] (not (empty? (:problems x))))]
    {:title   "Issues needing JIRA clean-up"
     :columns [:id :title :problems]
     :rows    (->> issues
                   (map add-attention-needed)
                   (filter needs-attention?))}))

(defn report-personal-development [issues]
  (let [count-of              (fn [& preds] (->> issues (filter (apply every-pred preds)) count))
        add-completion-status (fn [x] (assoc x :complete? (closed? x)))]
    {:title   "Personal development tasks"
     :columns [:id :title :complete?]
     :rows    (->> issues
                   (filter personal-development?)
                   (map add-completion-status))}))

(defn generate-daily-report
  "Generate the daily report for the current sprint."
  ([options]
   (let [{:keys [board-name sprint-name]} options
         sprint                           (jira/get-sprint-named board-name sprint-name)
         issues                           (jira/get-issues-in-sprint-named board-name sprint-name)]
     (generate-daily-report options (raw-issues issues sprint))))

  ([options issues]
   [;; (report-issues-blocked issues)
    ;; (report-issues-started issues)
    ;; (report-issues-in-progress issues)
    ;; (report-issues-ready-for-release issues)
    ;; (report-issues-closed issues)
    (report-buddying-commitments issues)
    (report-issues-needing-buddies issues)
    (report-issues-needing-sizing issues)
    (report-issues-needing-triage issues)
    (report-issues-needing-jira-clean-up issues)])) 

;; -----------------------------------------------------------------------------
;; Sprint report
;; -----------------------------------------------------------------------------

(defn report-user-level-task-issues [issues]
  {:title   "User level tasks committed to in this sprint"
   :columns [:id :title :points :discipline :work-complete?]
   :rows    (->> issues
                 (filter :user-level-task?)
                 (sort-by (juxt :discipline :work-complete? :id)))})

(defn report-user-level-task-statistics [issues]
  (let [count-of (fn [& preds] (->> issues
                                   (filter :user-level-task?)
                                   (filter (apply every-pred preds))
                                   count))]
    {:title   "Statistics for user level tasks committed to and delivered in this sprint"
     :columns [:discipline :committed :delivered]
     :rows    [{:discipline :engineering    :committed (count-of engineering?)    :delivered (count-of :work-complete? engineering?)}
               {:discipline :data-science   :committed (count-of data-science?)   :delivered (count-of :work-complete? data-science?)}
               {:discipline :infrastructure :committed (count-of infrastructure?) :delivered (count-of :work-complete? infrastructure?)}]}))

(defn report-business-level-task-issues [issues]
  {:title   "Business level tasks committed to in this sprint"
   :columns [:id :title :points :discipline :work-complete?]
   :rows    (->> issues
                 (filter :business-deliverable?)
                 (sort-by (juxt :discipline :work-complete? :id)))})

(defn report-business-level-task-statistics [issues]
  (let [total-points-of (fn [& preds] (->> issues
                                          (filter :business-deliverable?)
                                          (filter (apply every-pred preds))
                                          (map :points)
                                          (filter identity)
                                          (apply +)))]
    {:title   "Statistics for business level points committed to and delivered in this sprint"
     :columns [:discipline :committed :delivered]
     :rows    [{:discipline :engineering    :committed (total-points-of engineering?)    :delivered (total-points-of :work-complete? engineering?)}
               {:discipline :data-science   :committed (total-points-of data-science?)   :delivered (total-points-of :work-complete? data-science?)}
               {:discipline :infrastructure :committed (total-points-of infrastructure?) :delivered (total-points-of :work-complete? infrastructure?)}]}))

(defn report-buddying-statistics [issues]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))]
    {:title   "Statistics for tasks worked on which should have had buddies"
     :columns [:metric :total]
     :rows    [{:metric "With buddies"    :total (count-of :work-started? :buddiable? :buddied?)}
               {:metric "Without buddies" :total (count-of :work-started? :buddiable? (complement :buddied?))}]}))

(defn report-buddying-summary [issues]
  {:title   "Summary of tasks worked on which should have had buddies"
   :columns [:id :title :assignee :buddies]
   :rows    (->> issues
                 (filter (every-pred :work-started? :buddiable?))
                 (map buddies-to-str))})

(defn report-issues-summary [issues sprint]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))
        raised?  (fn [x] (:raised-in-sprint? x))]
    {:title   "Issue summary"
     :columns [:category :open :raised :closed]
     :rows [{:category "Story"   :open (count-of story?   open?) :raised (count-of story?   raised?) :closed (count-of story?   closed?)}
            {:category "Task"    :open (count-of task?    open?) :raised (count-of task?    raised?) :closed (count-of task?    closed?)}
            {:category "Subtask" :open (count-of subtask? open?) :raised (count-of subtask? raised?) :closed (count-of subtask? closed?)}
            {:category "Bug"     :open (count-of bug?     open?) :raised (count-of bug?     raised?) :closed (count-of bug?     closed?)}
            {:category "GDPR"    :open (count-of gdpr?    open?) :raised (count-of gdpr?    raised?) :closed (count-of gdpr?    closed?)}
            {:category "Total"   :open (count-of open?)          :raised (count-of raised?)          :closed (count-of closed?)}]}))

(defn report-issues-raised-in-sprint [issues sprint]
  {:title   "Issues raised in the sprint"
   :columns [:id :type :title]
   :rows    (filter :raised-in-sprint? issues)})

(defn report-issues-closed-in-sprint [issues sprint]
  {:title   "Issues closed in the sprint"
   :columns [:id :type :title :discipline :raised-in-sprint?]
   :rows    (->> issues
                 (filter :work-complete?)
                 (sort-by (juxt :discipline :raised-in-sprint? :id)))})

(defn generate-sprint-report
  "Generate the sprint summary report."
  ([options]
   (let [{:keys [board-name sprint-name]} options
         sprint                           (jira/get-sprint-named board-name sprint-name)
         issues                           (jira/get-issues-in-sprint-named board-name sprint-name)]
     (generate-sprint-report options (raw-issues issues sprint) sprint)))

  ([options issues sprint]
   [(report-user-level-task-issues issues)
    (report-user-level-task-statistics issues)
    (report-business-level-task-issues issues)
    (report-business-level-task-statistics issues)
    (report-buddying-statistics issues)
    (report-buddying-summary issues)
    (report-issues-summary issues sprint)
    (report-issues-raised-in-sprint issues sprint)
    (report-issues-closed-in-sprint issues sprint)]))

;; -----------------------------------------------------------------------------
;; Burndown
;; -----------------------------------------------------------------------------

(def- formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd E"))

(defn- format-date [date]
  (.format formatter date))

;; (defn- calculate-burndown-metrics [issues]
;;   (let [issues (filter (fn [x] (= (:id x) "SDPR-5103")) issues)]
;;     (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) (map :id) (string/join " ")))]
;;       {:open   (count-of open?)
;;        :closed (count-of closed?)
;;        :total  (count-of identity)})))

(defn- calculate-burndown-metrics [issues]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))]
    {:open   (count-of open?)
     :closed (count-of closed?)
     :total  (count-of identity)}))

(defn- calculate-burndown-metrics-at-date [date issues]
  (calculate-burndown-metrics (issues-at-date (.plus date 23 ChronoUnit/HOURS) issues)))

(defn- calculate-burndown [start-date end-date issues]
  (->> (date/timestream (date/truncate-to-days start-date) 1 ChronoUnit/DAYS)
       (take-while (fn [x] (and (before-or-equal? x (date/current-date)) (before-or-equal? x end-date))))
       (filter (fn [x] (date/working-day? x)))
       (map (fn [x] (calculate-burndown-metrics-at-date x issues)))))

(defn report-burndown [start-date end-date issues discipline]
  (let [data (calculate-burndown start-date end-date issues)]
    (map
     (partial string/join ",")
     (concat
      (map-indexed (fn [i x] [discipline "Remaining" i (:open x)]) data)
      (map-indexed (fn [i x] [discipline "Scope"     i (:total x)]) data)
      [[discipline "Ideal" 0 (:open (first data))]
       [discipline "Ideal" 9 0]]))))

(defn generate-burndown
  "Generate a burndown."
  ([options]
   (let [{:keys [board-name sprint-name]} options
         sprint                           (jira/get-sprint-named board-name sprint-name)
         issues                           (jira/get-issues-in-sprint-named board-name sprint-name)]
     (generate-burndown options sprint issues)))

  ([options sprint issues]
   (let [start-date      (:start-date sprint)
         end-date        (:end-date sprint)
         all-of          (fn [& preds] (filter (apply every-pred preds) issues))
         open-in-sprint? (fn [x] (open? (issue-at-date start-date x)))]
     (string/join "\n"
                  (concat
                   ["Discipline,Category,Day,Count"]
                   (report-burndown start-date end-date (all-of open-in-sprint? engineering?)    "Engineering")
                   (report-burndown start-date end-date (all-of open-in-sprint? infrastructure?) "Infrastructure")
                   (report-burndown start-date end-date (all-of open-in-sprint? data-science?)   "Data Science")
                   (report-burndown start-date end-date (all-of open-in-sprint? support?)        "Support"))))))

;; -----------------------------------------------------------------------------
;; Epic burndown
;; -----------------------------------------------------------------------------

(defn min-by [f coll]
  (when (seq coll)
    (apply min-key f coll)))

(defn max-by [f coll]
  (when (seq coll)
    (apply max-key f coll)))

(defn- calculate-epic-burndown-metrics [issues]
  (let [points-of (fn [& preds] (->> issues (filter (apply every-pred preds)) (map :points) (filter identity) (apply +)))]
    {:open   (points-of open?)
     :closed (points-of closed?)
     :total  (points-of identity)}))

(defn- calculate-epic-burndown-metrics-at-date [date issues]
  (calculate-epic-burndown-metrics (issues-at-date (.plus date 13 ChronoUnit/DAYS) issues)))

(defn report-epic-burndown [start-date end-date issues]
  (->> (date/timestream (date/truncate-to-days start-date) 14 ChronoUnit/DAYS)
       (take-while (fn [x] (and (before-or-equal? x (date/current-date)) (before-or-equal? x end-date))))
       (filter (fn [x] (date/working-day? x)))
       (map (fn [x] (calculate-epic-burndown-metrics-at-date x issues)))))

(defn generate-epic-burndown
  "Generate an epic burndown."
  ([options]
   (let [{:keys [board-name epic-id]} options
         epic                         (first (jira/get-issues-with-ids [epic-id]))
         issues                       (jira/get-issues-in-epic-with-id epic-id)]
     (generate-epic-burndown options epic issues)))

  ([options epic issues]
   (let [to-in-progress (fn [{:keys [field to]}] (and (= field "status") (or (contains? (jira/in-progress-states) to)
                                                                            (contains? (jira/closed-states) to))))
         to-closed      (fn [{:keys [field to]}] (and (= field "status") (contains? (jira/closed-states) to)))
         date-millis    (fn [x] (-> x (:date) (.toInstant) (.toEpochMilli)))
         start-date     (->> issues (mapcat :history) (filter to-in-progress) (min-by date-millis) (:date))
         end-date       (->> issues (mapcat :history) (filter to-closed)      (max-by date-millis) (:date))]
     (report-epic-burndown start-date end-date issues))))

;; -----------------------------------------------------------------------------
;; Buddy map
;; -----------------------------------------------------------------------------

(defn buddy-pairings [issues]
  (let [pairs (fn [{:keys [assignee buddies] :as issue}] (for [buddy buddies] [assignee buddy]))]
    (->> issues
         (mapcat pairs))))

(defn generate-buddy-map
  "Generate a buddy map."
  ([options]
   (let [{:keys [board-name sprint-name]} options
         sprint                           (jira/get-sprint-named board-name sprint-name)
         issues                           (jira/get-issues-in-sprint-named board-name sprint-name)]
     (generate-buddy-map options sprint issues)))

  ([options sprint issues]
   (let [historical-issues (issues-at-date (date/truncate-to-days (:end-date sprint)) issues)
         pairings          (buddy-pairings historical-issues)
         counts-by-pair    (into {} (for [[k v] (group-by identity pairings)] [k (count v)]))
         assignees         (->> issues (map :assignee))
         buddies           (->> issues (mapcat :buddies))
         all-users         (->> (concat assignees buddies) (filter some?) (into #{}))]
     (string/join "\n"
                  (concat ["Owner,Buddy,Count"]
                          (for [assignee all-users
                                buddy    all-users]
                            (string/join "," [assignee buddy (get counts-by-pair [assignee buddy] 0)])))))))

;; -----------------------------------------------------------------------------
;; Backlog
;; -----------------------------------------------------------------------------

(defn report-sized-and-unsized-deliverables [name issues]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))]
    {:title   (str name ": Sized and unsized open deliverables")
     :columns [:status :count]
     :rows    [{:status "Open and sized"   :count (count-of user-level-task? (complement closed?) sized?)}
               {:status "Open and unsized" :count (count-of user-level-task? (complement closed?) (complement sized?))}
               {:status "Open total"       :count (count-of user-level-task? (complement closed?))}]}))

(defn- to-age-in-days [issues]
  (map (fn [x] (.between java.time.temporal.ChronoUnit/DAYS (:created x) (date/current-date))) issues))

(defn- mean
  [& numbers]
    (if (empty? numbers)
      0
      (float (/ (reduce + numbers) (count numbers)))))

(defn report-deliverable-age-metrics [name issues]
  (let [issue-ages (->> issues (filter user-level-task?) to-age-in-days)]
    {:title   (str name ": Deliverable ages in days")
     :columns [:metric :age-in-days]
     :rows    [{:metric "Oldest" :age-in-days (if (empty? issue-ages) 0 (apply max issue-ages))}
               {:metric "Newest" :age-in-days (if (empty? issue-ages) 0 (apply min issue-ages))}
               {:metric "Mean"   :age-in-days (if (empty? issue-ages) 0 (apply mean issue-ages))}]}))

(defn- report-bucket-metrics [name issues]
  [(report-deliverable-age-metrics name issues)
   (report-sized-and-unsized-deliverables name issues)])

(defn- report-bucket-metrics [name issues]
  [(report-deliverable-age-metrics name issues)
   (report-sized-and-unsized-deliverables name issues)])

(defn- report-metrics-for-future-sprints [board-name]
  (let [get-metrics (fn [sprint-name]
                      (report-bucket-metrics sprint-name
                                             (jira/get-issues-in-sprint-named board-name sprint-name)))]
    (->> (jira/get-sprints board-name)
         (filter (fn [x] (= (:state x) "future")))
         (map :name)
         (sort)
         (mapcat get-metrics))))

(defn- report-metrics-for-backlog [board-name]
  (->> (jira/get-issues-in-backlog board-name)
       (report-bucket-metrics "Backlog")))

(defn report-epics-in-progress [issues]
  (let [count-of         (fn [xs & preds] (->> xs (filter (apply every-pred preds)) count))
        epics-to-stories (group-by :epic issues)
        epic-metrics     (into {} (for [[k v] epics-to-stories] [k {:open   (count-of v (complement closed?))
                                                                    :closed (count-of v closed?)}]))]
    {:title   "Epics currently in progress"
     :columns [:id :title :open :closed]
     :rows    (->> issues
                   (filter (every-pred epic? in-progress?))
                   (map (fn [{:keys [id] :as issue}] (merge issue (get epic-metrics id)))))}))

(defn report-epics-open [issues]
  (let [count-of         (fn [xs & preds] (->> xs (filter (apply every-pred preds)) count))
        epics-to-stories (group-by :epic issues)
        epic-metrics     (into {} (for [[k v] epics-to-stories] [k {:open   (count-of v (complement closed?))
                                                                    :closed (count-of v closed?)}]))]
    {:title   "Epics not yet started"
     :columns [:id :title :open :closed]
     :rows    (->> issues
                   (filter (every-pred epic? to-do?))
                   (map (fn [{:keys [id] :as issue}] (merge issue (get epic-metrics id)))))}))

(defn report-epic-counts-by-state [issues]
  (let [count-of (fn [& preds] (->> issues (filter (apply every-pred preds)) count))]
    {:title   "Epic counts by state"
     :columns [:status :count]
     :rows    [{:status "To-do"       :count (count-of epic? to-do?)}
               {:status "In progress" :count (count-of epic? in-progress?)}
               {:status "Closed"      :count (count-of epic? closed?)}]})) 

(defn generate-backlog-report
  "Generate a backlog report."
  [options]
  (let [{:keys [board-name project-name]} options]
    (concat
     (report-metrics-for-backlog board-name)
     (report-metrics-for-future-sprints board-name)
     (let [all-issues (jira/get-issues-in-project-named project-name)]
       [(report-epic-counts-by-state all-issues)
        (report-epics-open all-issues)
        (report-epics-in-progress all-issues)]))))

;; -----------------------------------------------------------------------------
;; TODO: Sort all this out
;; -----------------------------------------------------------------------------

(defn- calculate-story-lead-time [story tasks]
  (analysis/calculate-lead-time-in-days
   (assoc story :history (->> (mapcat :history tasks)
                              (sort-by :date)))))

;; TODO: Wire in
;; TODO: Should be if history is empty
;; TODO: Merge story-closed-state with other states
(defn- story-state [config tasks]
  (let [all-tasks-closed? (every? closed? tasks)
        no-tasks-started? (every? to-do? tasks)]
    (cond
      (all-tasks-closed? (every? closed? tasks)) (get-in config [:schema :story-closed-state])
      (no-tasks-started? (every? closed? tasks)) (get-in config [:schema :story-to-do-state])
      :else (get-in config [:schema :story-in-progress-state]))))

(defn- build-story-history-from-tasks [config story tasks]
  (assoc story
         :history (->> (mapcat :history tasks)
                       (sort-by :date))
         :status (story-state config tasks)))

(defn- story-metrics [issues]
  (let [stories-by-id   (->> (filter story? issues) (group-by :id) (map-vals first))
        issues-by-story (group-by :parent-id issues)]
    (for [[k vs] (dissoc issues-by-story nil)]
      (assoc (stories-by-id k)
             :tasks-open      (->> vs (filter (every-pred task? open?))   count)
             :tasks-closed    (->> vs (filter (every-pred task? closed?)) count)
             :bugs-open       (->> vs (filter (every-pred bug? open?))    count)
             :bugs-closed     (->> vs (filter (every-pred bug? closed?))  count)
             :story-lead-time (calculate-story-lead-time (stories-by-id k) (issues-by-story k))))))

(defn report-story-metrics [issues]
  (println "\nStories and tasks in this sprint")
  (pprint/print-table [:id :title :status :points :tasks-open :tasks-closed :bugs-open :bugs-closed :lead-time-in-days :story-lead-time]
                      (story-metrics issues)))







(defn report-task-time-in-state [issues]
  (println "\nTask lead time in working days and working hours in state")
  (pprint/print-table [:id :title :lead-time-in-days :todo :in-progress :blocked :deployment :other]
                      (->> issues
                           ;; (filter non-story?)
                           (map #(merge % (:time-in-state %))))))  



(defn generate-project-report
  "Generate a report for a project."
  ([config name]
   (let [issues (map analysis/add-derived-fields (jira/get-issues-in-project-named name))]
     (generate-project-report config name issues)))

  ([config name issues]
   (report-story-metrics issues)))

