#!/bin/bash

prename=st
measure=$1
endness=$2
percentage=$3

destname=${prename}-${measure}-${endness}-${percentage}

WSJ_ALL=~/workspace/parsers/wsj-only

# echo Measure=$measure Endness=$endness Percentage=$percentage Destination=$destname Source=$WSJ_ALL

echo Copying from base to $destname

# mkdir $destname

cp -RLp base $destname

cd $destname

pwd

echo Source=$WSJ_ALL Measure=$measure Endness=$endness Percentage=$percentage >metainfo.txt
echo scripts/prep_self_training.groovy $WSJ_ALL $measure $endness $percentage all_files >>metainfo.txt

cat metainfo.txt

echo Preparing self-training data in $destname/xtrain

scripts/prep_self_training.groovy $WSJ_ALL $measure $endness $percentage all_files

echo Submitting train_all job.

cd bllip-parser
pwd

condor_submit train_all.condor
