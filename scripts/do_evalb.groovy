#!/usr/bin/env groovy

workspace_dir = new File('/home2/jimwhite/workspace/parsers')

xcorpus_dir = new File(workspace_dir, 'xcorpus')
xcorpus_dir.mkdirs()

brown_mrg_dir = new File('/corpora/LDC/LDC99T42/RAW/parsed/mrg/brown')

file_list_file = new File(xcorpus_dir, 'filelist.txt')

bllip_dir = new File('bllip-parser')

tmp_dir = new File('tmp')

sysout_dir = new File(tmp_dir, 'parsed')

evalb_dir = new File(tmp_dir, 'evalb')
evalb_dir.delete()

all_file_paths = new File(xcorpus_dir, 'filelist.txt').readLines()

all_file_paths.each { evalb(it, new File(xcorpus_dir, it + '.eval')) }

all_files_name = "all_files"

all_the_gold = new File(tmp_dir, all_files_name + '.eval')
all_the_best = new File(sysout_dir, all_files_name + '.best')
all_the_nbest = new File(sysout_dir, all_files_name + '.nbest')

all_the_gold.delete()
all_the_best.delete()
all_the_nbest.delete()

all_file_paths.each { file_path ->
    all_the_gold << new File(xcorpus_dir, file_path + '.eval').text
    all_the_best << new File(sysout_dir, file_path + '.best').text
    all_the_nbest << new File(sysout_dir, file_path + '.nbest').text
}

evalb(all_files_name, all_the_gold)

//all_file_paths.each { String file_path ->
//    evalb([new File(tmp_dir, 'xcorpus/cf/cf03.mrg.best')], new File(evalb_dir, 'cf/cf03'))
//    evalb([new File(sysout_dir, file_path + '.best')], new File(xcorpus_dir, file_path + '.eval'))
//}

// bllip-parser/evalb/evalb -p bllip-parser/evalb/new.prm tmp/xcorpus/cf/cf05.mrg.eval tmp/xcorpus/cf/cf05.mrg.best

def evalb(String file_path, File evalb_gold)
{
    def best_file = new File(sysout_dir, file_path + '.best')
    def nbest_trees = new File(sysout_dir, file_path + '.nbest')
//    def reranker_output = new File(sysout_dir, file_path + '.best')
//    def evalb_gold = new File(xcorpus_dir, file_path + '.eval')

//    baseFile.parentFile.mkdirs()
//    nbest_trees.delete()
//    reranker_output.delete()
//    evalb_gold.delete()
//
//    best_files.each { File best ->
//        nbest_trees << new File(best.parentFile, best.name.replaceAll(/best$/, /nbest/)).text
//        reranker_output << best.text
//        evalb_gold << new File(best.parentFile, best.name.replaceAll(/best$/, /eval/)).text
//    }

    def outFile = new File(evalb_dir, file_path + '.evalb.txt')
    def errFile = new File(evalb_dir, file_path + '.err')
    outFile.parentFile.mkdirs()

    def command = ['bllip-parser/evalb/evalb', '-p', 'bllip-parser/evalb/new.prm', evalb_gold, best_file]

//     println command

    outFile.withOutputStream { stdout ->
        errFile.withOutputStream { stderr ->
            def proc = command.execute()
            proc.waitForProcessOutput(stdout, stderr)
            if (proc.exitValue()) {
                println "exitValue: ${proc.exitValue()}"
            }
        }
    }

    def logpFile = new File(evalb_dir, file_path + '.logp.txt')

    outFile.withReader { evalb_reader ->

        if (evalb_reader.readLine()) {
            // That should have been first of these two header lines:
            //   Sent.                        Matched  Bracket   Cross        Correct Tag
            // ID  Len.  Stat. Recal  Prec.  Bracket gold test Bracket Words  Tags Accracy
            // And this should be the second one:
            evalb_reader.readLine()

            def header_separator = evalb_reader.readLine()
            if (!header_separator.startsWith('===')) {
                println "Expected ${outFile.path} to be separator but got this instead:"
                println header_separator
                println()
            }

            logpFile.withPrintWriter { printer ->
                nbest_trees.withReader { tree_reader ->
                    String parse_line
                    while (parse_line = tree_reader.readLine()) {
                        def (_, parse_count, sentence_id0) = (parse_line =~ /(\d+)[^.]+\.(.+)/)[0]
                        parse_count = parse_count as Integer

                        def first_p = 0

                        parse_count.times { i ->
                            def p = tree_reader.readLine() as Double
                            def x = tree_reader.readLine()

                            if (!i) first_p = p
                        }

                        tree_reader.readLine()  // Should be an empty line

                        def evalb_line = evalb_reader.readLine()
                        def eval_matcher = evalb_line =~ /\s*(\d+)\s+(\d+)\s+(\d+)\s+([.\d]+)\s+([.\d]+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+([.\d]+)/
//                        if (!eval_matcher.matches()) { println "No match for $sentence_id0 ${nbest_trees.path}\n$evalb_line"}
                        def eval_match = eval_matcher[0]
                        def (__, sentence_id1, sent_len, status, recall, precision, matched_bracket, brackets_gold, brackets_test, cross_bracket, word_count, correct_tags, tag_accuracy) = eval_match

                        // These don't stay in sync when reading the merged report.
//                        sentence_id0 = sentence_id0 as Integer
//                        sentence_id1 = sentence_id1 as Integer
                        if (((status as Integer) == 0) /*&& (sentence_id0 == sentence_id1 - 1)*/) {
                            printer.println "${eval_match.tail().join('\t')}\t$first_p"
//                    printer.println "$sentence_id1\t$sent_len\t${sentence_id1-sentence_id0}\t$recall\t$precision\t$first_p"
                        }
                    }
                }
            }
        }
    }

}
