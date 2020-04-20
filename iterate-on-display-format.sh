#!/bin/bash

ls *.vg.json *.csv | entr -s '                                  \
    clear ;                                                     \
    rm output.png ;                                             \
    node_modules/vega-lite/bin/vl2png test.vg.json output.png ; \
    imgcat output.png'
