#!/bin/bash

prename=st
measure=$1
endness=$2

for percentage in 10 20 30 40 50 60 70 80 90
do 
	destname=${prename}-${measure}-${endness}-${percentage}

	pushd
	cd $destname
	pwd
	rm -f experiments.dag.*
	scripts/gondor.sh scripts/experiments
	condor_submit_dag experiments.dag
	condor_wait experiments.dag.dagman.log
	scripts/do_evalb.groovy
	popd
done
