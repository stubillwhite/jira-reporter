# jira-reporter

A simple script to pull information out of JIRA for the daily stand-up. Tested on JIRA 7.9.0, probably highly configuration dependent.

## Usage

- Edit `resources/config.edn`
- `lein uberjar`
- `./run-application.sh`

## Example output

    Issues blocked
    
    |    :id |            :title |  :assignee | :days-in-progress |
    |--------+-------------------+------------+-------------------|
    | ID-123 |       Do something| John Smith |                22 |
    | ID-234 | Do something else |   Jane Doe |                11 |
    
    Issues in progress
    
    |    :id |         :title |  :assignee | :days-in-progress |
    |--------+----------------+------------+-------------------|
    | ID-234 | Build a widget |   Jane Doe |                11 |
    
    Issues which changed state yesterday
    
    |    :id | :status |     :title |  :assignee | :days-in-progress |
    |--------+---------+------------+------------+-------------------|
    | ID-184 |    DONE |  Fix a bug | John Smith |                 4 |
    
    Issues awaiting deployment
    
    |    :id | :status |           :title | :assignee | :days-in-progress |
    |--------+---------+------------------+-----------+-------------------|
    | ID-187 |  Deploy |  Publish new API |  Jane Doe |                 4 |
