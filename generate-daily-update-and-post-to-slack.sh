#!/bin/bash

set -euo pipefail # Exit on error, undefined symbol, or errors in pipe

REPORTS_DIR=reports

IMG_BACKGROUND=gray10
IMG_FOREGROUND=gray80

function generate-report-and-post-to-slack() {
    declare team=$1 channel=$2

    rm -f ${team}-daily-report.txt
    rm -f ${team}-daily-report.png

    make daily-report-${team} > ${team}-daily-report.txt

    # Generate the PNG for Slack to avoid text wrapping
    #  - No idea how big it will be, so generate oversized and trim down
    #  - Ensure -annotate +x+y has a Y value at least pointsize

    convert                                         \
        -size 5000x5000                             \
        xc:${IMG_BACKGROUND}                        \
        -font "/System/Library/Fonts/Monaco.dfont"  \
        -pointsize 32                               \
        -fill ${IMG_FOREGROUND}                     \
        -annotate +32+60                            \
        "@${team}-daily-report.txt"                 \
        -trim                                       \
        -bordercolor ${IMG_BACKGROUND}              \
        -border 32                                  \
        +repage                                     \
        ${team}-daily-report.png

    slackcat --channel ${channel} ${team}-daily-report.png
    mv ${team}-daily-report.txt $REPORTS_DIR
    mv ${team}-daily-report.png $REPORTS_DIR
}

function generate-burndown-and-post-to-slack() {
    declare team=$1 channel=$2

    rm -f burndown.csv
    rm -f ${team}-burndown.png

    make burndown-${team}

    slackcat --channel ${channel} ${team}-burndown.png
    mv burndown.csv         $REPORTS_DIR
    mv ${team}-burndown.png $REPORTS_DIR
}

function generate-buddy-map-and-post-to-slack() {
    declare team=$1 channel=$2

    rm -f buddy-map.csv
    rm -f ${team}-buddy-map.png

    make buddy-map-${team}

    slackcat --channel ${channel} ${team}-buddy-map.png
    mv buddy-map.csv         $REPORTS_DIR
    mv ${team}-buddy-map.png $REPORTS_DIR
}

rm -rf $REPORTS_DIR
mkdir -p $REPORTS_DIR
generate-report-and-post-to-slack    Helix recs-helix
generate-burndown-and-post-to-slack  Helix recs-helix
generate-buddy-map-and-post-to-slack Helix recs-helix

generate-report-and-post-to-slack    Orion recs-orion
generate-burndown-and-post-to-slack  Orion recs-orion
generate-buddy-map-and-post-to-slack Orion recs-orion
open $REPORTS_DIR
