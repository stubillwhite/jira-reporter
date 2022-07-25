# vim:fdm=marker

# Constants                                                                 {{{1
# ==============================================================================

COLOR_RED=\033[0;31m
COLOR_GREEN=\033[0;32m
COLOR_YELLOW=\033[0;33m
COLOR_BLUE=\033[0;34m
COLOR_NONE=\033[0m
COLOR_CLEAR_LINE=\r\033[K

BOARD_NAME=CORE Tribe
PROJECT_NAME=Recommenders Team
SPRINT_PREFIX=Sprint 76

VEGA_LITE=node_modules/vega-lite/bin/vl2png

APP_JAR=jira-reporter-1.0.1-SNAPSHOT-standalone.jar

CMDSEP=;

# Targets                                                                   {{{1
# ==============================================================================

# Help                              {{{2
# ======================================

help:
	@grep -E '^[0-9a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "$(COLOR_BLUE)%-18s$(COLOR_NONE) %s\n", $$1, $$2}'

# Clean                             {{{2
# ======================================

.PHONY: clean
clean: ## Remove all artefacts
	@echo 'Cleaning application'
	@lein clean
	@rm -f ./${APP_JAR}

.PHONY: clean-all
clean-all: clean ## Remove all artefacts and dependencies
	@echo 'Cleaning dependencies'
	@rm -rf ./node_modules

# Application                       {{{2
# ======================================

${APP_JAR}:
	@echo 'Building application'
	@lein clean
	@lein test
	@lein uberjar 
	@mv target/${APP_JAR} ./${APP_JAR}

application: ${APP_JAR}

# vega-lite                         {{{2
# ======================================

${VEGA_LITE}:
	@echo 'Installing vega-lite'
	@npm install node-gyp vega-cli vega-lite

vega: ${VEGA_LITE}

# build                             {{{2
# ======================================

build: application vega

# buddy-map                         {{{2
# ======================================

buddy-map: build ## Generate buddy metrics
	@echo -------------------------------------------------------------------------------- 
	@echo -- Buddy-map
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}" --buddy-map > buddy-map.csv
	@${VEGA_LITE} buddy-map.vg.json > buddy-map.png
	@imgcat buddy-map.png

# burndown                          {{{2
# ======================================

burndown: build ## Generate burndown metrics
	@echo -------------------------------------------------------------------------------- 
	@echo -- Burndown
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}" --burndown > burndown.csv
	@${VEGA_LITE} burndown.vg.json > burndown.png
	@imgcat burndown.png

# daily-report                      {{{2
# ======================================

daily-report: build ## Generate daily reports
	@echo -------------------------------------------------------------------------------- 
	@echo -- Daily report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}" --daily-report

# sprint-report                     {{{2
# ======================================

sprint-report: build ## Generate sprint reports
	@echo -------------------------------------------------------------------------------- 
	@echo -- Sprint report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}" --sprint-report

# raw-sprint-report                 {{{2
# ======================================

raw-sprint-report: build ## Generate raw sprint reports
	@echo -------------------------------------------------------------------------------- 
	@echo -- Raw sprint report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX}" --sprint-report-raw --tsv

# backlog-report                    {{{2
# ======================================

.PHONY: backlog-report
backlog-report: build ## Generate backlog report
	@echo -------------------------------------------------------------------------------- 
	@echo -- Backlog report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --backlog-report --board-name "${BOARD_NAME}" --project-name "${PROJECT_NAME}"

# list-sprints                      {{{2
# ======================================

.PHONY: list-sprints
list-sprints: build  # List the sprints
	@echo -------------------------------------------------------------------------------- 
	@echo -- Sprints report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --list-sprints --board-name "${BOARD_NAME}"
