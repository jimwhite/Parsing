#!/usr/bin/env groovy

bllip_dir = new File('bllip-parser')

tmp_dir = new File('tmp')

sysout_dir = new File(tmp_dir, 'xcorpus')

all_files = sysout_dir.listFiles().grep { it.isDirectory() }.collectMany { it.listFiles().grep { it.name.endsWith ".best" } }

evalb_dir = new File(tmp_dir, 'evalb')

//evalb(all_files, new File(evalb_dir, 'xcorpus'))

all_files.each { File best ->
    // evalb([new File(tmp_dir, 'xcorpus/cf/cf03.mrg.best')], new File(evalb_dir, 'cf/cf03'))
    evalb([best], new File(evalb_dir, best.parentFile.name + '/' + (best.name - /\.mrg.best$/)))
}


// bllip-parser/evalb/evalb -p bllip-parser/evalb/new.prm tmp/xcorpus/cf/cf05.mrg.eval tmp/xcorpus/cf/cf05.mrg.best

def evalb(List<File> best_files, File baseFile)
{
    def nbest_trees = new File(baseFile.parentFile, baseFile.name + '.nbest')
    def reranker_output = new File(baseFile.parentFile, baseFile.name + '.best')
    def evalb_gold = new File(baseFile.parentFile, baseFile.name + '.eval')

    baseFile.parentFile.mkdirs()
    nbest_trees.delete()
    reranker_output.delete()
    evalb_gold.delete()

    best_files.each { File best ->
        nbest_trees << new File(best.parentFile, best.name.replaceAll(/best$/, /nbest/)).text
        reranker_output << best.text
        evalb_gold << new File(best.parentFile, best.name.replaceAll(/best$/, /eval/)).text
    }

    def outFile = new File(baseFile.parentFile, baseFile.name + '.evalb.txt')
    def errFile = new File(baseFile.parentFile, baseFile.name + '.err')

    def command = ['bllip-parser/evalb/evalb', '-p', 'bllip-parser/evalb/new.prm', evalb_gold, reranker_output]

    outFile.withOutputStream { stdout ->
        errFile.withOutputStream { stderr ->
            def proc = command.execute()
            proc.consumeProcessOutput(stdout, stderr)
            proc.waitFor()
            if (proc.exitValue()) {
                println "exitValue: ${proc.exitValue()}"
            }
        }
    }

    def logpFile = new File(baseFile.parentFile, baseFile.name + '.logp.txt')

    def evalb_lines = outFile.text.readLines()

    if (evalb_lines) {
        evalb_lines.remove(0)   //   Sent.                        Matched  Bracket   Cross        Correct Tag
        evalb_lines.remove(0)   // ID  Len.  Stat. Recal  Prec.  Bracket gold test Bracket Words  Tags Accracy
        def header_separator = evalb_lines.remove(0)
        if (!header_separator.startsWith('===')) {
            println "Expected ${outFile.path} to be separator but got this instead:"
            println header_separator
            println()
        }

        logpFile.withPrintWriter { printer ->
            def parse_lines = nbest_trees.readLines() // .collect { it.trim() }.grep { it }

            while (parse_lines) {
                def h = parse_lines.remove(0)

                if (h) {
                    def (_, parse_count, sentence_id0) = (h =~ /(\d+)[^.]+\.(.+)/)[0]
                    parse_count = parse_count as Integer

                    def first_p = 0

                    parse_count.times { i ->
                        def p = parse_lines.remove(0) as Double
                        def x = parse_lines.remove(0)

                        if (!i) first_p = p
                    }

                    parse_lines.remove(0)

                    def evalb_line = evalb_lines.remove(0)
                    def eval_matcher = evalb_line =~ /\s*(\d+)\s+(\d+)\s+(\d+)\s+([.\d]+)\s+([.\d]+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+([.\d]+)/
                    def eval_match = eval_matcher[0]
                    def (__, sentence_id1, sent_len, status, recall, precision, matched_bracket, brackets_gold, brackets_test, cross_bracket, word_count, correct_tags, tag_accuracy) = eval_match

                    sentence_id0 = sentence_id0 as Integer
                    sentence_id1 = sentence_id1 as Integer
                    if (((status as Integer) == 0) && (sentence_id0 == sentence_id1 - 1)) {
                        printer.println "${eval_match.join('\t')}\t$first_p"
//                    printer.println "$sentence_id1\t$sent_len\t${sentence_id1-sentence_id0}\t$recall\t$precision\t$first_p"
                    }
                }
            }
        }
    }

}
