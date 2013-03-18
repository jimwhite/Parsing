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

workspace_dir = new File('/home2/jimwhite/workspace/parsers')

// *Don't* use the BLLIP parser in the workspace.  Use the one in this parsing experiment directory.
bllip_dir = new File('bllip-parser')

xcorpus_dir = new File(workspace_dir, 'xcorpus')
xcorpus_dir.mkdirs()

brown_mrg_dir = new File('/corpora/LDC/LDC99T42/RAW/parsed/mrg/brown')

tmp_dir = new File('tmp')

sysout_dir = new File(tmp_dir, 'parsed')
sysout_dir.mkdirs()

/////////////////////////////
// Condor Command Definitions
/////////////////////////////

// convert_ptb = gondor.condor_command(new File(bllip_dir, 'second-stage/programs/prepare-data/ptb'), ['mode', 'from.in'])

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

file_list_file = new File(xcorpus_dir, 'filelist.txt')

file_list_file.eachLine { String file_path ->
    def section_dir = new File(sysout_dir, file_path).parentFile
    section_dir.mkdirs()

    def charniak_input = new File(xcorpus_dir, file_path + ".sent")
    def evalb_gold = new File(xcorpus_dir, file_path + ".eval")

//    if (!charniak_input.exists()) convert_ptb(mode:'-c', from:original_ptb, outfile:charniak_input)
//    if (!evalb_gold.exists()) convert_ptb(mode:'-e', from:original_ptb, outfile:evalb_gold)

    def nbest_output = new File(sysout_dir, file_path + '.nbest')
    def reranker_output = new File(sysout_dir, file_path + '.best')

    // parse_nbest(model:PARSER_MODEL, input:charniak_input) >> tee(nbest_output) >> rerank_parses(features: RERANKER_FEATURES, weights: RERANKER_WEIGHTS, outfile:reranker_output)
    // charniak_input >> parse_nbest(model:PARSER_MODEL) >> nbest_output >> rerank_parses(features: RERANKER_FEATURES, weights: RERANKER_WEIGHTS) >> reranker_output

    parse_nbest(model:PARSER_MODEL, input:charniak_input, outfile:nbest_output)
    rerank_parses(features: RERANKER_FEATURES, weights: RERANKER_WEIGHTS, infile:nbest_output, outfile:reranker_output)
}
