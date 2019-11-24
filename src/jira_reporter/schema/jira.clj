(ns jira-reporter.schema.jira
  (:require [clojure.spec.alpha :as spec]
            [expound.alpha :as expound])
  (:import java.time.ZonedDateTime))

;; -----------------------------------------------------------------------------
;; Common
;; -----------------------------------------------------------------------------

(defn- zoned-date-time? [x] (instance? ZonedDateTime x))

;; -----------------------------------------------------------------------------
;; Sprint
;; -----------------------------------------------------------------------------

(spec/def :sprint/id        int?)
(spec/def :sprint/name      string?)
(spec/def :sprint/state     string?)
(spec/def :sprint/startDate zoned-date-time?)
(spec/def :sprint/endDate   zoned-date-time?)

(spec/def ::sprint (spec/keys :req-un [:sprint/id
                                       :sprint/name
                                       :sprint/state
                                       :sprint/startDate
                                       :sprint/endDate]))

;; -----------------------------------------------------------------------------
;; Issue
;; -----------------------------------------------------------------------------

;; Common
(spec/def :issue/key     string?)
(spec/def :issue/name    string?)
(spec/def :issue/created zoned-date-time?)

(spec/def :issue-fields/parent        (spec/keys :req-un [:issue/key]))
(spec/def :issue-fields/displayName   string?)
(spec/def :issue-fields/assignee      (spec/nilable (spec/keys :req-un [:issue-fields/displayName])))
(spec/def :issue-fields/issuetype     (spec/keys :req-un [:issue/name]))
(spec/def :issue-fields/status        (spec/keys :req-un [:issue/name]))
(spec/def :issue-fields/summary       string?)
(spec/def :issue-fields/labels        (spec/coll-of string?))
(spec/def :issue-fields/sprint        (spec/nilable ::sprint))
(spec/def :issue-fields/closedSprints (spec/coll-of ::sprint))

(spec/def :issue/fields (spec/keys :req-un [:issue-fields/assignee
                                            :issue-fields/issuetype
                                            :issue-fields/status
                                            :issue-fields/summary
                                            :issue-fields/sprint]
                                   :opt-un [:issue-fields/parent]))

(spec/def :issue-changelog/startAt    int?)
(spec/def :issue-changelog/maxResults int?)
(spec/def :issue-changelog/total      int?)

(spec/def :issue-histories/created         zoned-date-time?)
(spec/def :issue-histories-item/field      string?)
(spec/def :issue-histories-item/fromString (spec/nilable string?))
(spec/def :issue-histories-item/toString   (spec/nilable string?))
(spec/def :issue-histories/items           (spec/coll-of (spec/keys :req-un [:issue-histories-item/field :issue-histories-item/fromString :issue-histories-item/toString])))

(spec/def :issue-changelog/histories (spec/coll-of (spec/keys :req-un [:issue-histories/created
                                                                       :issue-histories/items])))

(spec/def :issue/changelog (spec/keys :req-un [:issue-changelog/startAt
                                               :issue-changelog/maxResults
                                               :issue-changelog/total
                                               :issue-changelog/histories]))

(spec/def ::issue (spec/keys :req-un [:issue/key
                                      :issue/fields
                                      :issue/changelog]
                             :opt-un [:issue/created]))
