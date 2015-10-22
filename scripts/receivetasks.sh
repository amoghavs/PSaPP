#!/usr/bin/env bash

# example 1: receivetasks.sh /projects/pmac/apps/PSaPP /projects/pmac/ftp/pub/incoming/pmacdata /pmacdata/ /pmacdata/scratch /pmacdata/logs/jReceive_logs/
# example 2: receivetasks.sh /usr/local/app/PSaPP /pmaclabs/ftp/incoming/ /pmaclabs/ti11_test /pmaclabs/scratch /pmaclabs/logs/jReceive_logs/

psapp_root=$1
inbox=$2
storage=$3
scratch=$4
logdir=$5

set -e

if [ "$psapp_root" == "" -o "$inbox" == "" -o "$storage" == "" -o "$scratch" == "" -o "$logdir" == "" ]; then
    echo "usage: $0 <psapp_root> <inbox> <storage> <scratch> <logdir>";
    exit -1;
fi

check_valid_dir() {  if [ ! -d $1 ]; then echo "directory not found: $1"; exit -1; fi; }
check_valid_dir $psapp_root
check_valid_dir $inbox
check_valid_dir $storage
check_valid_dir $scratch
check_valid_dir $logdir

export PSAPP_ROOT=$psapp_root
recv_cmd="$psapp_root/bin/jReceive --inbox $inbox --primary $storage --scratch_dir $scratch"
empty_str="Information: No valid done files to process so exiting"
unq_str=`date +%F_%T`
log_file="$logdir/jreceive.$unq_str.log"
lock_file="$inbox/jReceive.lock"
remove_empty=1

if [ ! -f $lock_file ]; then
# run receive command, writing to a log file
    touch $lock_file
    $recv_cmd 2>&1 | tee $log_file
    rm -f $lock_file

# if no files were processed, remove the log file
    if [ $remove_empty = 1 ]; then
	grep "$empty_str" $log_file > /dev/null 2>&1
	if [ $? = 0 ]; then
	    rm -f $log_file
	fi
    fi
fi
