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

// Each parser has it's own binary, but we'll use the one in base for them all.
bllip_dir = new File(workspace_dir, 'base/bllip-parser')

ensemble_dir = new File('ensemble')

ycorpus_dir = new File(workspace_dir, 'ycorpus')

/////////////////////////////
// Condor Command Definitions
/////////////////////////////

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

['brown-train.mrg'].each { String file_path ->
    ensemble_dir.eachFileMatch(~/parser_.*/) { File parser_dir ->
        def sysout_dir = new File(parser_dir, 'tmp/parsed')
        sysout_dir.deleteDir()
        sysout_dir.mkdirs()

        def nbest_output = new File(sysout_dir, file_path + '.nbest')
        def reranker_output = new File(sysout_dir, file_path + '.best')

        def charniak_input = new File(ycorpus_dir, file_path + ".sent")
//        def evalb_gold = new File(ycorpus_dir, file_path + ".eval")


        parse_nbest(model:PARSER_MODEL, input:charniak_input, outfile:nbest_output)
        rerank_parses(features: RERANKER_FEATURES, weights: RERANKER_WEIGHTS, infile:nbest_output, outfile:reranker_output)
    }
}
