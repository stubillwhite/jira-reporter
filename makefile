# Constants

COLOR_RED=\033[0;31m
COLOR_GREEN=\033[0;32m
COLOR_YELLOW=\033[0;33m
COLOR_BLUE=\033[0;34m
COLOR_NONE=\033[0m
COLOR_CLEAR_LINE=\r\033[K

BOARD_NAME=CORE Tribe
PROJECT_NAME=SD Personalized Recommender
SPRINT_PREFIX=Sprint 7 
SQUAD_NAMES=Hulk Storm Flash

SQUAD_PROJECTS=$(addprefix project-,${SQUAD_NAMES})
SQUAD_DAILY_REPORTS=$(addprefix daily-report-,${SQUAD_NAMES})
SQUAD_SPRINT_REPORTS=$(addprefix sprint-report-,${SQUAD_NAMES})

APP_JAR=jira-reporter-0.1.6-SNAPSHOT-standalone.jar

CMDSEP=;

# Targets

help:
	@grep -E '^[0-9a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "$(COLOR_BLUE)%-15s$(COLOR_NONE) %s\n", $$1, $$2}'

${APP_JAR}:
	@echo 'Building application'
	@lein clean
	@lein uberjar
	@mv target/${APP_JAR} ./${APP_JAR}

build: ${APP_JAR}

.PHONY: project-report 
project-report: build ${SQUAD_PROJECTS} ## Generate project metrics

${SQUAD_PROJECTS}: project-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* project
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}$*" --project --tsv

.PHONY: daily-report
daily-report: build ${SQUAD_DAILY_REPORTS} ## Generate daily reports

${SQUAD_DAILY_REPORTS}: daily-report-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* daily report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}$*" --daily-report

.PHONY: sprint-report
sprint-report: build ${SQUAD_SPRINT_REPORTS} ## Generate sprint reports

.PHONY: ${SQUAD_SPRINT_REPORTS}
${SQUAD_SPRINT_REPORTS}: sprint-report-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* sprint report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}$*" --sprint-report

.PHONY: backlog-report
backlog-report: build ## Generate backlog report
	@echo -------------------------------------------------------------------------------- 
	@echo -- Backlog report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --backlog-report "${PROJECT_NAME}" --tsv
