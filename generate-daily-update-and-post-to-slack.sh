#!/bin/bash

set -euo pipefail # Exit on error, undefined symbol, or errors in pipe

REPORTS_DIR=reports

IMG_BACKGROUND=gray10
IMG_FOREGROUND=gray80

function generate-report-and-post-to-slack() {
    declare channel=$1

    rm -f daily-report.txt
    rm -f daily-report.png

    make daily-report > daily-report.txt

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
        "@daily-report.txt"							\
        -trim                                       \
        -bordercolor ${IMG_BACKGROUND}              \
        -border 32                                  \
        +repage                                     \
        daily-report.png

    slackcat --channel ${channel} daily-report.png
    mv daily-report.txt $REPORTS_DIR
    mv daily-report.png $REPORTS_DIR
}

function generate-burndown-and-post-to-slack() {
    declare channel=$1

    rm -f burndown.csv
    rm -f burndown.png

    make burndown

    slackcat --channel ${channel} burndown.png
    mv burndown.csv $REPORTS_DIR
    mv burndown.png $REPORTS_DIR
}

function generate-buddy-map-and-post-to-slack() {
    declare channel=$1

    rm -f buddy-map.csv
    rm -f buddy-map.png

    make buddy-map

    slackcat --channel ${channel} buddy-map.png
    mv buddy-map.csv $REPORTS_DIR
    mv buddy-map.png $REPORTS_DIR
}

rm -rf $REPORTS_DIR
mkdir -p $REPORTS_DIR
generate-report-and-post-to-slack    recommenders-team
generate-burndown-and-post-to-slack  recommenders-team
generate-buddy-map-and-post-to-slack recommenders-team
