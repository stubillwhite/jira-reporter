(ns jira-reporter.schema
  (:require [clojure.spec.alpha :as spec]
            ;; [clojure.spec.gen.alpha :as gen]
            [expound.alpha :as expound]
            [jira-reporter.date :refer :all])
  (:import [java.time Instant ZonedDateTime ZoneId]))

;; -----------------------------------------------------------------------------
;; Common
;; -----------------------------------------------------------------------------

(defn- zoned-date-time? [x] (instance? ZonedDateTime x))

(spec/def ::id string?)

;; -----------------------------------------------------------------------------
;; Sprint
;; -----------------------------------------------------------------------------

(spec/def ::sprint
  (spec/keys :req-un [::id ::name ::start-date ::end-date ::state]))

(defn to-zoned-date-time [ms]
  (-> (Instant/ofEpochMilli ms) (ZonedDateTime/ofInstant utc)))

(defn- year-to-ms-since-epoch [year]
  (-> (ZonedDateTime/of year 1 1 0 0 0 0 utc) (.toInstant) (.toEpochMilli)))

(def min-date (year-to-ms-since-epoch 2015))
(def max-date (year-to-ms-since-epoch 2030))

;; (def zoned-date-time-gen
;;   (gen/generate (gen/fmap to-zoned-date-time (gen/choose min-date max-date))))
;; 
;; (spec/def ::zoned-date-time (spec/with-gen zoned-date-time? zoned-date-time-gen))

(spec/def ::name       string?)
(spec/def ::state      string?)
(spec/def ::start-date (spec/nilable zoned-date-time?))
(spec/def ::end-date   (spec/nilable zoned-date-time?))
;; (spec/def ::start-date (spec/nilable ::zoned-date-time))
;; (spec/def ::end-date   (spec/nilable ::zoned-date-time))

;; (gen/generate (spec/gen ::sprint))

;; -----------------------------------------------------------------------------
;; Issue
;; -----------------------------------------------------------------------------

(spec/def ::created           zoned-date-time?)
(spec/def ::parent-id         (spec/nilable string?))
(spec/def ::subtask-ids       (spec/coll-of string?))
(spec/def ::type              string?)
(spec/def ::status            string?)
(spec/def ::assignee          (spec/nilable string?))
(spec/def ::buddy             (spec/coll-of string?))
(spec/def ::title             string?)
(spec/def ::points            (spec/nilable float?))
(spec/def ::team              (spec/nilable string?))
(spec/def ::epic              (spec/nilable string?))
(spec/def ::labels            (spec/coll-of string?))
(spec/def ::history           (spec/coll-of map?))   ;; TODO: Should be history elements
(spec/def ::current-sprint    (spec/nilable ::sprint))
(spec/def ::closed-sprints    (spec/coll-of ::sprint))
(spec/def ::lead-time-in-days (spec/nilable int?))
(spec/def ::time-in-state     (spec/map-of keyword? int?))

(spec/def ::issue
  (spec/keys :req-un [::id ::created ::parent-id ::subtask-ids ::type ::status ::assignee ::buddy ::title ::points ::team ::epic ::labels ::history ::current-sprint ::closed-sprints]))

(spec/def ::enriched-issue
  (spec/keys :req-un [::id ::created ::parent-id ::subtask-ids ::type ::status ::assignee ::buddy ::title ::points ::team ::epic ::labels ::history ::current-sprint ::closed-sprints ::lead-time-in-days ::time-in-state]))

;; -----------------------------------------------------------------------------
;; Config
;; -----------------------------------------------------------------------------

(spec/def :nrepl/host                 string?)
(spec/def :nrepl/port                 int?)
(spec/def ::nrepl                     (spec/keys :req-un [:nrepl/host :nrepl/port]))
(spec/def :jira/username              string?)
(spec/def :jira/password              string?)
(spec/def :jira/server                string?)
(spec/def :jira/batch-size            int?)
(spec/def ::jira                      (spec/keys :req-un [:jira/username :jira/password :jira/server :jira/batch-size]))
(spec/def ::cache-filename            string?)
(spec/def :custom-fields/epic-link    string?)
(spec/def :custom-fields/story-points string?)
(spec/def ::custom-fields             (spec/keys :req-un [:custom-fields/epic-link :custom-fields/story-points]))
(spec/def ::schema                    map?)

(spec/def ::config
  (spec/keys :req-un [::nrepl ::jira ::cache-filename ::custom-fields ::schema]))

;; -----------------------------------------------------------------------------
;; Enable spec with pretty explanations
;; -----------------------------------------------------------------------------

(spec/check-asserts true)
;; (set! spec/*explain-out* expound/printer)


