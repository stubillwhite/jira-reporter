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
PROJECT_NAME=SD Personalized Recommender
SPRINT_PREFIX=Sprint 44
SQUAD_NAMES=Helix Orion

SQUAD_BURNDOWNS=$(addprefix burndown-,${SQUAD_NAMES})
SQUAD_BUDDY_MAPS=$(addprefix buddy-map-,${SQUAD_NAMES})
SQUAD_DAILY_REPORTS=$(addprefix daily-report-,${SQUAD_NAMES})
SQUAD_SPRINT_REPORTS=$(addprefix sprint-report-,${SQUAD_NAMES})
SQUAD_RAW_SPRINT_REPORTS=$(addprefix raw-sprint-report-,${SQUAD_NAMES})

VEGA_LITE=node_modules/vega-lite/bin/vl2png

APP_JAR=jira-reporter-0.1.22-SNAPSHOT-standalone.jar

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

# Application                       {{{2
# ======================================

${APP_JAR}:
	@echo 'Building application'
	@lein clean
	@lein test
	@lein uberjar 
	@mv target/${APP_JAR} ./${APP_JAR}

build: ${APP_JAR}

# vega-lite                         {{{2
# ======================================

${VEGA_LITE}:
	@echo 'Installing vega-lite'
	@npm install node-gyp vega-cli vega-lite

vega: ${VEGA_LITE}

# buddy-map                         {{{2
# ======================================

.PHONY: buddy-map 
buddy-map: build vega ${SQUAD_BUDDY_MAPS} ## Generate buddy metrics

${SQUAD_BUDDY_MAPS}: buddy-map-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* buddy-map
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX} $*" --buddy-map > buddy-map.csv
	@${VEGA_LITE} buddy-map.vg.json > $*-buddy-map.png
	@imgcat $*-buddy-map.png

buddy-map-%: ${VEGA_LITE}

# burndown                          {{{2
# ======================================

.PHONY: burndown 
burndown: build vega ${SQUAD_BURNDOWNS} ## Generate burndown metrics

${SQUAD_BURNDOWNS}: burndown-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* burndown
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX} $*" --burndown > burndown.csv
	@${VEGA_LITE} burndown.vg.json > $*-burndown.png
	@imgcat $*-burndown.png

burndown-%: ${VEGA_LITE}

# daily-report                      {{{2
# ======================================

.PHONY: daily-report
daily-report: build ${SQUAD_DAILY_REPORTS} ## Generate daily reports

${SQUAD_DAILY_REPORTS}: daily-report-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* daily report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX} $*" --daily-report

# sprint-report                     {{{2
# ======================================

.PHONY: sprint-report
sprint-report: build ${SQUAD_SPRINT_REPORTS} ## Generate sprint reports

.PHONY: ${SQUAD_SPRINT_REPORTS}
${SQUAD_SPRINT_REPORTS}: sprint-report-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* sprint report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX} $*" --sprint-report

# raw-sprint-report                 {{{2
# ======================================

.PHONY: raw-sprint-report
raw-sprint-report: build ${SQUAD_RAW_SPRINT_REPORTS} ## Generate raw sprint reports

.PHONY: ${SQUAD_RAW_SPRINT_REPORTS}
${SQUAD_RAW_SPRINT_REPORTS}: raw-sprint-report-%:
	@echo -------------------------------------------------------------------------------- 
	@echo -- $* raw sprint report
	@echo -------------------------------------------------------------------------------- 
	@echo 
	@./jira-reporter --board-name "${BOARD_NAME}" --sprint-name "${SPRINT_PREFIX} $*" --sprint-report-raw --tsv

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
