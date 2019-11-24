(ns jira-reporter.schema.domain
  (:require [clojure.spec.alpha :as spec]
            [expound.alpha :as expound])
  (:import java.time.ZonedDateTime))

;; -----------------------------------------------------------------------------
;; Common
;; -----------------------------------------------------------------------------

(defn- zoned-date-time? [x] (instance? ZonedDateTime x))

(spec/def ::id string?)

;; -----------------------------------------------------------------------------
;; Sprint
;; -----------------------------------------------------------------------------

(spec/def ::name       string?)
(spec/def ::state      string?)
(spec/def ::start-date zoned-date-time?)
(spec/def ::end-date   zoned-date-time?)

(spec/def ::sprint
  (spec/keys :req-un [::id ::name ::start-date ::end-date ::state]))

;; -----------------------------------------------------------------------------
;; Issue
;; -----------------------------------------------------------------------------

(spec/def ::created           zoned-date-time?)
(spec/def ::parent-id         (spec/nilable string?))
(spec/def ::subtask-ids       (spec/coll-of string?))
(spec/def ::type              string?)
(spec/def ::status            string?)
(spec/def ::assignee          (spec/nilable string?))
(spec/def ::title             string?)
(spec/def ::points            (spec/nilable float?))
(spec/def ::epic              (spec/nilable string?))
(spec/def ::labels            (spec/coll-of string?))
(spec/def ::history           (spec/coll-of map?))   ;; TODO: Should be history elements
(spec/def ::sprints           (spec/coll-of ::sprint))
(spec/def ::lead-time-in-days (spec/nilable int?))
(spec/def ::time-in-state     (spec/map-of keyword? int?))

(spec/def ::issue
  (spec/keys :req-un [::id ::created ::parent-id ::subtask-ids ::type ::status ::assignee ::title ::points ::epic ::history]))

(spec/def ::enriched-issue
  (spec/keys :req-un [::id ::created ::parent-id ::subtask-ids ::type ::status ::assignee ::title ::points ::epic ::history ::lead-time-in-days ::time-in-state]))

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
