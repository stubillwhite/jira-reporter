#!/bin/bash

set -euo pipefail # Exit on error, undefined symbol, or errors in pipe

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

    ## Integration currently broken
    ##
    ## slackcat --channel ${channel} ${team}-daily-report.png
    ## rm ${team}-daily-report.txt
    ## rm ${team}-daily-report.png
}

function generate-burndown-and-post-to-slack() {
    declare team=$1 channel=$2

    rm -f burndown.csv
    rm -f ${team}-burndown.png

    make burndown-${team}

    ## Integration currently broken
    ##
    ## slackcat --channel ${channel} ${team}-burndown.png
    ## rm -f burndown.csv
    ## rm -f ${team}-burndown.png
}

function generate-buddy-map-and-post-to-slack() {
    declare team=$1 channel=$2

    rm -f buddy-map.csv
    rm -f ${team}-buddy-map.png

    make buddy-map-${team}

    ## Integration currently broken
    ##
    ## slackcat --channel ${channel} ${team}-buddy-map.png
    ## rm -f buddy-map.csv
    ## rm -f ${team}-buddy-map.png
}

generate-report-and-post-to-slack    Helix recs-helix
generate-burndown-and-post-to-slack  Helix recs-helix
generate-buddy-map-and-post-to-slack Helix recs-helix
