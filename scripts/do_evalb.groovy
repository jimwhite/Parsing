#!/usr/bin/env groovy

workspace_dir = new File('/home2/jimwhite/workspace/parsers')

xcorpus_dir = new File(workspace_dir, 'xcorpus')
xcorpus_dir.mkdirs()

brown_mrg_dir = new File('/corpora/LDC/LDC99T42/RAW/parsed/mrg/brown')

file_list_file = new File(xcorpus_dir, 'filelist.txt')

bllip_dir = new File('bllip-parser')

tmp_dir = new File('tmp')

sysout_dir = new File(tmp_dir, 'parsed')

LABELS_TO_DELETE = ["TOP", "S1", "-NONE-", ",", ":", "``", "''", "."] as Set

evalb_dir = new File(tmp_dir, 'evalb')
evalb_dir.delete()

all_file_paths = new File(xcorpus_dir, 'filelist.txt').readLines()

// all_file_paths.each { evalb(it, new File(xcorpus_dir, it + '.eval')) }

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

    def log_2 = Math.log(2)

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
                best_file.withReader { best_tree_reader ->
                evalb_gold.withReader { gold_tree_reader ->
                nbest_trees.withReader { nbest_tree_reader ->
                    String parse_line
                    while (parse_line = nbest_tree_reader.readLine()) {
                        def (_, parse_count, sentence_id0) = (parse_line =~ /(\d+)[^.]+\.(.+)/)[0]
                        parse_count = parse_count as Integer

                        def best_tree = canonical_tree_string(best_tree_reader.readLine())
                        def gold_tree = canonical_tree_string(gold_tree_reader.readLine())

                        // Which of the n-best was picked by the reranker?
                        def best_index = -1
                        // Which of the n-best (if any) matches the gold annotation.
                        def gold_index = -1

                        def parse_log2_p = []

                        parse_count.times { i ->
                            def p = nbest_tree_reader.readLine() as Double
                            def x = canonical_tree_string(nbest_tree_reader.readLine())


                            if (best_index < 0 && x == best_tree) best_index = i

                            if (gold_index < 0 && x == gold_tree) gold_index = i

                            parse_log2_p << p
                        }

                        // Sort the log probs so that the following calculations work from smallest value to largest.
                        parse_log2_p.sort(true)

                        def log2_sum_of_parse_p = log2_sum(parse_log2_p)

                        def log2_sentence_entropy = log2_entropy_from_logp(parse_log2_p)

                        nbest_tree_reader.readLine()  // Should be an empty line

                        def evalb_line = evalb_reader.readLine()
                        def eval_matcher = evalb_line =~ /\s*(\d+)\s+(\d+)\s+(\d+)\s+([.\d]+)\s+([.\d]+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+([.\d]+)/
//                        if (!eval_matcher.matches()) { println "No match for $sentence_id0 ${nbest_trees.path}\n$evalb_line"}
                        def eval_match = eval_matcher[0]
                        def (__, sentence_id1, sent_len, status, recall, precision, matched_bracket, brackets_gold, brackets_test, cross_bracket, word_count, correct_tags, tag_accuracy) = eval_match

                        precision = precision as Double
                        recall = recall as Double
                        tag_accuracy = tag_accuracy as Double

                        sent_len = sent_len as Integer

                        def f_measure = (precision + recall) ? 2 * (precision * recall) / (precision + recall) : 0
                        def sentence_entropy = Math.pow(2, log2_sentence_entropy)
                        def word_entropy = sentence_entropy / sent_len
                        def log2_word_entropy = log2_sentence_entropy - (Math.log(sent_len) / log_2)
                        def per_word_log2_entropy = log2_sentence_entropy / sent_len

                        if (f_measure == 100 && tag_accuracy == 100 && gold_index < 0) println "Didn't find gold for $sentence_id1\nbest: $best_tree\ngold: $gold_tree\n"

                        // These don't stay in sync when reading the merged report.
//                        sentence_id0 = sentence_id0 as Integer

                        def sentence_index = (sentence_id1 as int) - 1
                        def sentence_index_mod10 = sentence_index % 10

                        if (((status as Integer) == 0) /*&& (sentence_id0 == sentence_id1 - 1)*/) {
                            printer.println "${eval_match.tail().join('\t')}\t-99\t$sentence_index_mod10\t$f_measure\t$log2_sentence_entropy\t$per_word_log2_entropy\t$log2_word_entropy\t$sentence_entropy\t$word_entropy\t$best_index\t$gold_index\t$log2_sum_of_parse_p\t${parse_log2_p.size()}\t${parse_log2_p.join('\t')}"
//                    printer.println "$sentence_id1\t$sent_len\t${sentence_id1-sentence_id0}\t$recall\t$precision\t$first_p"
                        }
                    }
                } // nbest_tree_reader
                } // best_tree_reader
                } // gold_tree_reader
            }
        }
    }
}

def canonical_tree_string(String tree)
{
    def sexpList = readSexpList(new StringReader(tree))

    if (sexpList.size() == 1) {
        clean_tree(sexpList.head()).toString()
    } else {
        System.err.println("Didn't get exactly one tree from readSexpList.\n" + sexpList)
        tree
    }
}

def clean_tree(tree)
{
    if (tree instanceof List) {
        if (tree.head() in LABELS_TO_DELETE) {
            if (tree.size() == 2) {
                if (tree[1] instanceof List) {
                    tree = clean_tree(tree[1])
                } else {
                    tree = []
                }
            } else {
//                System.err.println("Tried to delete label ${tree.head()} but length is ${tree.size()} for:\n" + tree)
                tree = tree.tail().collect { clean_tree(it) }.grep { it }

                if (tree.size() == 1) tree = tree.head()
//                println tree
            }
        } else {
            tree = [clean_label(tree.head())] + tree.tail().collect { clean_tree(it) }.grep { it }

            if (tree.size() == 1) tree = []
        }
    }

    tree
}

def clean_label(String label)
{
    label = label.replaceFirst(/[-=].*$/, '')

    // EQ_LABEL ADVP PRT
    if (label == 'PRT') label = 'ADVP'

    label
}

def readSexpList(Reader reader)
{
    // This grammar has single quotes in token names.
//    final tokenDelimiters = "\"''()\t\r\n "
//    final tokenDelimiters = "\"()\t\r\n "
    // No quoted strings at all for these s-exprs.
    final tokenDelimiters = "()\t\r\n "

    def stack = []
    def sexps = []

    def cint = reader.read()

    loop:
    while (cint >= 0) {
        Character c = cint as Character
        switch (c) {

            case ')' :
                if (stack.size() < 1) break loop
                def t = stack.pop()
                t << sexps
                sexps = t
                cint = reader.read()
                break

            case '(':

                stack.push(sexps)
                sexps = []
                cint = reader.read()
                break

            default:
                if (c.isWhitespace()) {
                    cint = reader.read()
                } else {
                    def token = new StringBuilder()
                    token.append(c)
                    while ((cint = reader.read()) >= 0) {
                        if (tokenDelimiters.indexOf(cint) >= 0) break
                        token.append(cint as Character)
                    }
                    sexps << token.toString()
                }
        }
    }

    return sexps
}


/*
[[0.5,0.5],[0.4, 0.1, 0.5]].each { l ->
    def log_2 = Math.log(2)
    def log2_e = Math.log(Math.E) / log_2
//    println "$l = ${l.sum()} ${Math.log(l.sum()) / log_2}"
    println "$l log2 h=${Math.log(entropy(l))/log_2} h=${entropy(l)} log h=${Math.log(entropy(l))}"
    def ll = l.collect { log2(it) }.sort()
    println ll
    println "${ll.collect { Math.exp(it / log2_e )}} ${ll.collect { Math.pow(2, it)}}"
//    def y = 0.5
//    def x = y * Math.log(y) / log_2
//    println "$x ${Math.log(-x)} ${Math.log(-x)/log_2} ${y *log2(y)}"
//    println "$y ${log2(y)} ${log2(-log2(y))} ${log2(y) + log2(-log2(y))} ${Math.pow(2, log2(y) + log2(-log2(y)))}"
    def log_h_ll = log2_entropy_from_logp(ll)
    println "$ll = log2 h=${log_h_ll} h=${Math.pow(2, log_h_ll)}"
//    def logplp_ll = ll.collect { it + log2(-it) }
//    println logplp_ll
//    log_h_ll = log2_sum(logplp_ll)
//    println "$ll = log2 h=${log_h_ll} h=${Math.pow(2, log_h_ll)} ${Math.exp(Math.pow(2, log_h_ll))}"

    println()
}
return
*/

def entropy(l) { -l.sum { it * (Math.log(it)/Math.log(2))}}


//Double log2_entropy_from_logp(List<Double> parse_p) {
////    -parse_p.sum { it * Math.log(it) }
//    def log_2 = Math.log(2)
//    Double log2_e = Math.log(Math.E) / log_2
//
//    if (parse_p.size()) {
////        This should do the same thing as reverse.
////        parse_p = parse_p.sort()
//
//        Double ln_x0 = parse_p.head() / log2_e
//        ln_x0 += Math.log(-ln_x0)
//
//        // We assume list is already sorted so we go from smallest to largest.
//        Double ln_h = parse_p.tail().inject(ln_x0) { Double ln_z, Double lg_xi ->
//            def ln_xi = lg_xi / log2_e
//            ln_xi += Math.log(-ln_xi)
//            (Math.max(ln_z, ln_xi) + Math.log1p(Math.exp(-Math.abs(ln_z - ln_xi))))
//        }
//
//        println "$ln_h ${Math.exp(ln_h)} ${ln_h/log_2} ${Math.pow(2, ln_h/log_2)}"
//
//        (ln_h / log_2)
//    } else {
//        0
//    }
//}

def log2(x) { Math.log(x) / Math.log(2) }

Double log2_entropy_from_logp(List<Double> parse_p) {
    log2_sum(parse_p.collect { it + log2(-it)} )
}

Double log2_sum(List<Double> summands) {
//    -parse_p.sum { it * Math.log(it) }
    def log_2 = Math.log(2)
    def log2_e = Math.log(Math.E) / log_2

    if (summands.size()) {
        // We assume list is already sorted so we go from smallest to largest.
        Double t = summands.tail().inject(summands.head() / log2_e) { Double z, Double x ->
            x = x / log2_e
            Math.max(z, x) + Math.log1p(Math.exp(-Math.abs(z - x)))
        }

        (t / log_2)
    } else {
        Double.NEGATIVE_INFINITY
    }
}


/*
[1, 2, 3, 4, 0.5, 0.25, 0.125].each { println "$it ${Math.log(it) / Math.log(2)}"}
[[1e-10, 2e-11, 5e-14, 3e-12, 4e-13], [1, 2, 3, 4, 0.5, 0.25, 0.125]].each { l ->
    def log_2 = Math.log(2)
    def log2_e = Math.log(Math.E) / log_2
    println "$l = ${l.sum()} ${Math.log(l.sum()) / log_2}"
    def ll = l.collect { Math.log(it) / Math.log(2) }.sort()
    println "${ll.collect { Math.exp(it / log2_e )}}"
    println "$ll = ${log2_sum(ll)} ${Math.pow(2, log2_sum(ll))}"

}
assert log2_sum([0, -1, -2, -3] as List<Double>) == (Math.log(15) / Math.log(2))
*/

def f_zipf_eng(l) { 1 * l * Math.pow(0.9, l) }

//60.times { println "$it ${f_zipf_eng(it)}"}
//return

