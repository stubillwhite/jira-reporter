(ns jira-reporter.jira-client
  (:require [clj-http.client :as client]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn get-issue-details
  "Query the JIRA API for the details of issue with the specified ID."
  [{{:keys [username password server project]} :jira} id]
  (debug "Retrieving history for" id)
  (let  [url   (str "https://" server "/rest/api/2/issue/" id)]
    (client/get url {:query-params {:expand "changelog"} :basic-auth [username password]})))

(defn get-jql-query-results
  "Query the JIRA API with the specified JQL."
  [{{:keys [username password server project]} :jira} query]
  (debug (str"Executing query '" query "'"))
  (let [url   (str "https://" server "/rest/api/2/search")]
    (client/get url {:query-params {:jql query :maxResults 500} :basic-auth [username password]})))
