(ns jira-reporter.schema
  (:require [clojure.spec.alpha :as spec]))

(spec/check-asserts true)

(spec/def ::id string?)
(spec/def ::parent-id (spec/nilable string?))
(spec/def ::type string?)
(spec/def ::status string?)
(spec/def ::assignee (spec/nilable string?))
(spec/def ::title string?)
(spec/def ::history coll?) ;; TODO: Should be history elements
;; (spec/def ::lead-time-in-days (spec/nilable int?))
;; (spec/def ::time-in-state (spec/map-of keyword? int?))

(spec/def ::issue
  (spec/keys :req-un [::id ::parent-id ::type ::status ::assignee ::title ::history ;; ::lead-time-in-days ::time-in-state
                      ]))
