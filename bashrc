#!/usr/bin/env bash

psappdir="`pwd`/`dirname $BASH_SOURCE`"
export PSAPP_ROOT=$psappdir
echo "***** initializing environment using PSAPP_ROOT=$PSAPP_ROOT"
export CLASSPATH=`ls $PSAPP_ROOT/external_libs/*.jar | tr [:space:] :`$CLASSPATH
export PATH=${PSAPP_ROOT}/bin:${PATH}
