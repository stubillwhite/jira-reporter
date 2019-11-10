#!/bin/bash

set -euo pipefail # Exit on error, undefined symbol, or errors in pipe

IMG_BACKGROUND=gray10
IMG_FOREGROUND=gray80

function generate-report-and-post-to-slack() {
    declare team=$1 channel=$2

    make daily-report-${team} > daily-report-${team}.txt

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
        "@daily-report-${team}.txt"                 \
        -trim                                       \
        -bordercolor ${IMG_BACKGROUND}              \
        -border 32                                  \
        +repage                                     \
        daily-report-${team}.png

    slackcat --channel ${channel} daily-report-${team}.png
}

generate-report-and-post-to-slack Hulk  green-squad
generate-report-and-post-to-slack Flash red-squad
