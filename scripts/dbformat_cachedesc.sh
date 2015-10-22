#!/usr/bin/env bash
## turns a CacheDescriptions.txt file into pmac database input
## toggle the mode variable below to get jDB or direct psql output
##
## example using jDB mode:
##    $ dbformat_cachedesc.sh CacheDescriptions_min.txt > foo; jDB --action add --type CacheStructures --input foo
##
## example using psql mode:
##    $ dbformat_cachedesc.sh CacheDescriptions_min.txt > foo; VERBOSITY=1 psql -d pmacdata pmacdata -f foo
##

#mode=jdb
mode=psql


cache_desc=$1
print_usage() { 
    echo "error: $1"; 
    echo "usage: $0 <cache_descriptions>"; 
    echo ""; }

if [ "$cache_desc" == "" ]; then
    print_usage "command line argument <cache_descriptions> not given"
    exit -1
fi

# clear out comments/whitespace
grep -v "^#" $cache_desc | grep . > filtered1

# text replace to compute values in kb/mb
sed s/KB/"\*1024"/g filtered1 > filtered2
sed s/MB/"\*1024\*1024"/g filtered2 > filtered3

i=0
while read inp; do
    i=$[$i+1]
## [IN] [sysid] [lvl_count] L1[size assoc line repl] L2[size assoc line repl] L3[size assoc line repl]
    sysid=`echo $inp | awk '{ print $1 }'`
    lvls=`echo $inp | awk '{ print $2 }'`
    if [ $lvls -lt 1 -o $lvls -gt 3 ]; then
        print_usage "line $i claims $lvls levels of cache... valid range is [1,3]"
    fi
    size1=$[`echo $inp | awk '{ print $3 }'`]
    assoc1=$[`echo $inp | awk '{ print $4 }'`]
    line1=$[`echo $inp | awk '{ print $5 }'`]
    repl1=`echo $inp | awk '{ print $6 }'`
    comment=`echo $inp | awk '{ for (i=8;i<=NF;i++){ printf("%s ", $i) } }'`
    if [ $lvls -gt 1 ]; then
        size2=$[`echo $inp | awk '{ print $7 }'`]
        assoc2=$[`echo $inp | awk '{ print $8 }'`]
        line2=$[`echo $inp | awk '{ print $9 }'`]
        repl2=`echo $inp | awk '{ print $10 }'`
        comment=`echo $inp | awk '{ for (i=12;i<=NF;i++){ printf("%s ", $i) } }'`
    fi
    if [ $lvls -gt 2 ]; then
        size3=$[`echo $inp | awk '{ print $11 }'`]
        assoc3=$[`echo $inp | awk '{ print $12 }'`]
        line3=$[`echo $inp | awk '{ print $13 }'`]
        repl3=`echo $inp | awk '{ print $14 }'`
        comment=`echo $inp | awk '{ for (i=16;i<=NF;i++){ printf("%s ", $i) } }'`
    fi

    if [ "$mode" == "jdb" ]; then
## [jDB] l1_size,l2_size,l3_size,l1_associativity,l2_associativity,l3_associativity,l1_linesize,l2_linesize,l3_linesize,l1_replacement_policy,l2_replacement_policy,l3_replacement_policy,comments
        echo "$size1,$size2,$size3,$assoc1,$assoc2,$assoc3,$line1,$line2,$line3,$repl1,$repl2,$repl3,'$comment'";
    else
        echo "insert into cache_structures (dbid,l1_size,l1_associativity,l1_linesize,l1_replacement_policy,l2_size,l2_associativity,l2_linesize,l2_replacement_policy,l3_size,l3_associativity,l3_linesize,l3_replacement_policy,comments) values ($sysid,$size1,$assoc1,$line1,'$repl1',$size2,$assoc2,$line2,'$repl2',$size3,$assoc3,$line3,'$repl3','$comment');"
    fi

done < filtered3

rm filtered?
