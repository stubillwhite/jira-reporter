# Constants

COLOR_RED=\033[0;31m
COLOR_GREEN=\033[0;32m
COLOR_YELLOW=\033[0;33m
COLOR_BLUE=\033[0;34m
COLOR_NONE=\033[0m
COLOR_CLEAR_LINE=\r\033[K

BOARD_NAME=CORE Tribe
PROJECT_NAME=SD Personalized Recommender
SPRINT_PREFIX=Sprint 4 
SQUAD_NAMES=Hulk Storm Flash

SQUAD_BURNDOWNS=$(addprefix burndown-,${SQUAD_NAMES})
SQUAD_DAILY_REPORTS=$(addprefix daily-report-,${SQUAD_NAMES})
SQUAD_SPRINT_REPORTS=$(addprefix sprint-report-,${SQUAD_NAMES})

CMDSEP=;

# Targets

help:
	@grep -E '^[0-9a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "$(COLOR_BLUE)%-15s$(COLOR_NONE) %s\n", $$1, $$2}'

.PHONY: burndown 
burndown: ${SQUAD_BURNDOWNS} ## Generate burndown metrics

${SQUAD_BURNDOWNS}: burndown-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* burndown
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}$*" --burndown --tsv

.PHONY: daily-report
daily-report: ${SQUAD_DAILY_REPORTS} ## Generate daily reports

${SQUAD_DAILY_REPORTS}: daily-report-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* daily report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}$*" --daily-report

.PHONY: sprint-report
sprint-report: ${SQUAD_SPRINT_REPORTS} ## Generate sprint reports

.PHONY: ${SQUAD_SPRINT_REPORTS}
${SQUAD_SPRINT_REPORTS}: sprint-report-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* sprint report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}$*" --sprint-report

.PHONY: backlog-report
backlog-report: ## Generate backlog report
	@echo -------------------------------------------------------------------------------- 
	@echo -- Backlog report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --backlog-report "${PROJECT_NAME}" --tsv
