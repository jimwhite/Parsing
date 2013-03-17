// James White mailto:jimwhite@uw.edu

/////////////////////////////
// Environmental Dependencies
/////////////////////////////

// If there are environment variables you want to copy from the current process, use clone_environment:
// gondor.clone_environment('PATH', 'ANT_HOME', 'JAVA_HOME')
// If you want to copy *all* of the the current environment variables, omit the variable names (not recommended):
// gondor.clone_environment()

gondor.environment = [PATH:"/usr/kerberos/bin:/usr/local/bin:/bin:/usr/bin:/opt/git/bin:/opt/scripts:/condor/bin"
        , LC_COLLATE:'C'  // first-stage/PARSE/parseIt needs this to deal with punctuation correctly!
                            // Not really.  Need to use -K (pretokenized) flag.
]

/////////////
// Data Files
/////////////

bllip_dir = new File('bllip-parser')

brown_mrg_dir = new File('xcorpus')

test_original_ptb_files = brown_mrg_dir.listFiles().grep { it.isDirectory() }.collectMany { it.listFiles().grep { it.name.endsWith ".mrg" } }

tmp_dir = new File('tmp')

/////////////////////////////
// Condor Command Definitions
/////////////////////////////

convert_ptb = gondor.condor_command(new File(bllip_dir, 'second-stage/programs/prepare-data/ptb'), ['mode', 'from.in'])

PARSER_MODEL=new File(bllip_dir, 'first-stage/DATA/EN/')
MODELDIR=new File(bllip_dir, 'second-stage/models/ec50spnonfinal')
ESTIMATORNICKNAME='cvlm-l1c10P1'
RERANKER_WEIGHTS = new File(MODELDIR, ESTIMATORNICKNAME + '-weights.gz')
RERANKER_FEATURES = new File(MODELDIR, 'features.gz')

// first-stage/PARSE/parseIt -l399 -N50 first-stage/DATA/EN/ $*
parse_nbest = gondor.condor_command(new File(bllip_dir, 'first-stage/PARSE/parseIt'), ['-K.flag', '-l400.flag', '-N50.flag', 'model.in', 'input.in'])

// second-stage/programs/features/best-parses" -l "$MODELDIR/features.gz" "$MODELDIR/$ESTIMATORNICKNAME-weights.gz"
rerank_parses = gondor.condor_command(new File(bllip_dir, 'second-stage/programs/features/best-parses'), ['-l.flag', 'features.in', 'weights.in', 'infile.in'])

//////////////////////
// Job DAG Definitions
//////////////////////

println test_original_ptb_files

test_original_ptb_files.each { File original_ptb ->
    def work_dir = new File(tmp_dir, original_ptb.parentFile.path)
    work_dir.mkdirs()

    def charniak_input = new File(work_dir, original_ptb.name + ".sent")
    def evalb_gold = new File(work_dir, original_ptb.name + ".eval")
    def nbest_output = new File(work_dir, original_ptb.name + '.nbest')
    def reranker_output = new File(work_dir, original_ptb.name + '.best')

    if (!charniak_input.exists()) convert_ptb(mode:'-c', from:original_ptb, outfile:charniak_input)
    if (!evalb_gold.exists()) convert_ptb(mode:'-e', from:original_ptb, outfile:evalb_gold)

    // parse_nbest(model:PARSER_MODEL, input:charniak_input) >> tee(nbest_output) >> rerank_parses(features: RERANKER_FEATURES, weights: RERANKER_WEIGHTS, outfile:reranker_output)
    // charniak_input >> parse_nbest(model:PARSER_MODEL) >> nbest_output >> rerank_parses(features: RERANKER_FEATURES, weights: RERANKER_WEIGHTS) >> reranker_output

    parse_nbest(model:PARSER_MODEL, input:charniak_input, outfile:nbest_output)
    rerank_parses(features: RERANKER_FEATURES, weights: RERANKER_WEIGHTS, infile:nbest_output, outfile:reranker_output)
}
