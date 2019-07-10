(ns jira-reporter.schema
  (:require [clojure.spec.alpha :as spec]
            [mount.core :refer [defstate]])
  (:import java.time.ZonedDateTime))

;; TODO: Should refer to the literals from the config file

(defn- zoned-date-time? [x]
  (instance? ZonedDateTime x))

(spec/def ::id string?)
(spec/def ::created zoned-date-time?)
(spec/def ::parent-id (spec/nilable string?))
(spec/def ::type string?)
(spec/def ::status string?)
(spec/def ::assignee (spec/nilable string?))
(spec/def ::title string?)
(spec/def ::history (spec/coll-of map?)) ;; TODO: Should be history elements
(spec/def ::lead-time-in-days (spec/nilable int?))
(spec/def ::time-in-state (spec/map-of keyword? int?))

;; TODO: Extract subtasks, too

(spec/def ::issue
  (spec/keys :req-un [::id ::created ::parent-id ::type ::status ::assignee ::title ::history]))

(spec/def ::enriched-issue
  (spec/keys :req-un [::id ::created ::parent-id ::type ::status ::assignee ::title ::history ::lead-time-in-days ::time-in-state]))

(spec/check-asserts true)
