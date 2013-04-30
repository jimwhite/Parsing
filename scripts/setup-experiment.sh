#!/bin/bash

prename=sepa
measure=$1
endness=$2
percentage=$3

destname=${prename}-${measure}-${endness}-${percentage}

# WSJ_ALL=~/workspace/parsers/wsj-only

best_file=/home2/jimwhite/workspace/parsers/ensemble/brown-train.mrg.1ofE.best
stats_file=/home2/jimwhite/workspace/parsers/ensemble/brown-train.mrg.sepa

wsj_train=/home2/jimwhite/workspace/parsers/wsj_train.mrg

// Tuning for first stage parser.
// The first 500 of the first of every four sentences from wsj_sec23.mrg
wsj_tune=/home2/jimwhite/workspace/parsers/wsj_sec23-sel500.mrg

echo Measure=$measure Endness=$endness Percentage=$percentage Destination=$destname
echo best_file=$best_file
echo stats_file=stats_file

echo Copying from base to $destname

# Directory must not exist, otherwise the cp will copy base into a subdir of destname.
# rm -rf $destname

cp -RLp base $destname

pushd $destname

echo Source=$WSJ_ALL Measure=$measure Endness=$endness Percentage=$percentage >metainfo.txt
echo scripts/prep_self_training.groovy $measure $endness $percentage stats_file best_file >>metainfo.txt

cat metainfo.txt

echo Preparing self-training data in $destname/bllip-parser

// Select bllip-parser/xtrain and bllip-parser/xtune sentences from self-parsed data.
scripts/prep_self_training.groovy $measure $endness $percentage stats_file best_file

echo Submitting train_all job.

cd bllip-parser
pwd

cp $wsj_train xtrain
cp $wsj_tune xtune

# condor_submit train_all.condor

popd
