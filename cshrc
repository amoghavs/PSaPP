#!/usr/bin/env csh

setenv PSAPP_ROOT `pwd`
echo "***** initializing environment using PSAPP_ROOT=$PSAPP_ROOT"
if ($?CLASSPATH) then
    setenv CLASSPATH `ls $PSAPP_ROOT/external_libs/*.jar | tr "\n" :`$CLASSPATH
else
    setenv CLASSPATH `ls $PSAPP_ROOT/external_libs/*.jar | tr "\n" :`
endif    
setenv PATH ${PSAPP_ROOT}/bin:${PATH}
