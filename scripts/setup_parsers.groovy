#!/usr/bin/env groovy

bllip_parser_dir = new File(args[0])

split_wsj_data_dir = new File(args[1])

parser_ensemble_dir = new File(args[2])
parser_ensemble_dir.deleteDir()
parser_ensemble_dir.mkdirs()

ensemble_K = 20 // Make 20 parsers.

training_J = 8  // Use 8 of the splits for training each parser.

mrg_files = split_wsj_data_dir.listFiles().grep { it.name =~ /\.mrg$/ }.sort()

ensemble_K.times { setup_parser(it) }

def setup_parser(parser_i)
{
    def parser_dir = new File(parser_ensemble_dir, String.format('parser_%02d', parser_i))
    // parser_dir.mkdir()
    copy_files(bllip_parser_dir, parser_dir)

    def xtrain_dir = new File(parser_dir, 'xtrain')
    xtrain_dir.mkdir()
    training_J.times { train_i ->
        File src_file = mrg_files[(train_i + parser_i) % mrg_files.size()]
        File dst_file = new File(xtrain_dir, src_file.name)
        // Don't use symlinks cuz second-stage/programs/prepare-data/ptb won't cope with 'em.
        //        create_symlink(src_file, dst_file)
        copy_files(src_file, dst_file)
    }

    def xtune_dir = new File(parser_dir, 'xtune')
    xtune_dir.mkdir()
    File src_file = mrg_files[(parser_i + ((mrg_files.size() + training_J)/ 2).intValue()) % mrg_files.size()]
    File dst_file = new File(xtune_dir, src_file.name)
//    create_symlink(src_file, dst_file)
    copy_files(src_file, dst_file)
}

static def copy_files(File src_dir, File dst_dir) {
//    String cmd = System.getProperty('os.name') == 'Mac OS X' ? "cp -RLp $src_dir/ $dst_dir" :   "cp -RLp $src_dir $dst_dir"
    String cmd = "cp -RLp $src_dir $dst_dir"
    def proc = cmd.execute()
    if (proc.waitFor()) {
        print "Error: '$cmd' = ${proc.exitValue()}"
    }
    proc.exitValue()
}

static def create_symlink(File src_file, File dst_file) {
    def cmd = "ln -s ${src_file.absolutePath} $dst_file"
    def ln_proc = cmd.execute()
    if (ln_proc.waitFor()) {
        println "Error: '$cmd' = ${ln_proc.exitValue()}"
    }
    ln_proc.exitValue()
}

/*

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
*/
