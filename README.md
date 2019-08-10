# jira-reporter

A simple script to pull information out of JIRA for the daily stand-up. Tested on JIRA 7.9.0, probably highly configuration dependent.

## Build instructions

- Edit `resources/config.edn`
    - Add credentials
    - Add JIRA states
- `lein uberjar`
- `cp target/jira-reporter-0.1.4-SNAPSHOT-standalone.jar .`

Run the tool with the script `./jira-reporter`.

## Usage

    Usage: jira-reporter [options]
    
    Options:
          --list-boards       List the names of the boards
          --list-sprints      List the names of the sprints
          --sprint-report     Generate a report for the sprint
          --daily-report      Generate a daily status report for the sprint
          --burndown          Generate a burndown for the sprint
          --sprint-name NAME  Use sprint named NAME instead of the current sprint
          --board-name NAME   Use board named NAME
          --tsv               Output the data as TSV for Excel
      -h, --help

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
