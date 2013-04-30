#!/usr/bin/env groovy

workspace_dir = new File('/home2/jimwhite/workspace/parsers')

mrg_file_name = 'brown-train.mrg'

// Each parser has it's own binary, but we'll use the one in base for them all.
bllip_parser_dir = new File(workspace_dir, 'base/bllip-parser')

ensemble_dir = new File(workspace_dir, 'ensemble')

parser_dir_list = ensemble_dir.listFiles().findAll { it.name =~ /parser_.*/ }.sort { it.name }

ensemble_K = parser_dir_list.size()

println ensemble_K

best_parse_readers = parser_dir_list.collect { File parser_dir ->
    def sysout_dir = new File(parser_dir, 'tmp/parsed')
    def best_parse_file = new File(sysout_dir, mrg_file_name + '.best')
    best_parse_file.newReader()
}

// Some meaningless seed.
random = new Random(0xbc329e631aL)

selected_best_file = new File(ensemble_dir, mrg_file_name + '.1ofE.best')

selected_best_file.withPrintWriter { printer ->
    List<String> parses
    while ((parses = best_parse_readers.collect { it.readLine() }).any()) {
        printer.println parses[random.nextInt(ensemble_K)]
    }
}

best_parse_readers.each { it.close() }

selected_evalb_gold_file = new File(ensemble_dir, mrg_file_name + '.1ofE.eval')

prepare_ptb('-e', selected_best_file, selected_evalb_gold_file)

parser_evalb_file_list = parser_dir_list.collect { File parser_dir ->
    def sysout_dir = new File(parser_dir, 'tmp/parsed')
    evalb(selected_best_file, new File(sysout_dir, mrg_file_name + '.best'), new File(sysout_dir, mrg_file_name + '.evalb.txt'))
}

sepa_file = new File(ensemble_dir, mrg_file_name + '.sepa')

def evalb_readers = parser_evalb_file_list.collect { evalb_reader(it) }

def F_SCORE_FIELD = 0
def ID_FIELD = 1

sepa_file.withPrintWriter { printer ->
    List<List> evaluations
    while ((evaluations = evalb_readers.collect { read_next_evaluation(it) }).every()) {
        def id0 = evaluations[0][ID_FIELD]
        def id_match = evaluations.collect { it[ID_FIELD] == id0 }
        if (!id_match.every()) {
            println "Not all sentence_ids match:"
            evaluations.each { println it }
            break
        }

        def f_scores = evaluations.collect { it[F_SCORE_FIELD] }
        f_scores.remove(100 as Double)
        if (f_scores.size() != ensemble_K - 1) {
            println "Didn't get K-1 f-scores after removing a 100% $f_scores"
            evaluations.each { println it }
            break
        }
        def sepa = f_scores.sum() / (ensemble_K - 1)
        printer.println "${evaluations[0][ID_FIELD]}\t$sepa"
    }
}

evalb_readers.each { it.close() }

BufferedReader evalb_reader(File evalb_file)
{
    def evalb_reader = evalb_file.newReader()

    // EvalB report header lines:
    //   Sent.                        Matched  Bracket   Cross        Correct Tag
    // ID  Len.  Stat. Recal  Prec.  Bracket gold test Bracket Words  Tags Accracy

    expect_contains(evalb_reader.readLine(), "Sent.")
    expect_contains(evalb_reader.readLine(), "Bracket")
    expect_contains(evalb_reader.readLine(), "===")

    evalb_reader
}

def prepare_ptb(String switches, File src_file, File dst_file)
{
    def command = ['second-stage/programs/prepare-data/ptb', switches, src_file]

    println command

    dst_file.withOutputStream { stdout ->
        def stderr = new ByteArrayOutputStream()
        def proc = command.execute(null, bllip_parser_dir)
        proc.waitForProcessOutput(stdout, stderr)
        if (stderr.size()) System.err.println(stderr.toString())
        if (proc.exitValue()) {
            System.err.println "exitValue: ${proc.exitValue()}"
        }
    }

    dst_file
}

def evalb(File evalb_gold, File best_file, File outFile)
{
    def errFile = new File(outFile.parentFile, (outFile.name - ~/\.txt$/)+ '.err')

    def command = ['evalb/evalb', '-p', 'evalb/new.prm', evalb_gold, best_file]

    println command

    outFile.withOutputStream { stdout ->
        errFile.withOutputStream { stderr ->
            def proc = command.execute(null, bllip_parser_dir)
            proc.waitForProcessOutput(stdout, stderr)
            if (proc.exitValue()) {
                println "exitValue: ${proc.exitValue()}"
            }
        }
    }

    outFile
}

def expect_contains(String o, String e)
{
    if (!o.contains(e)) { println "EvalB header mismatch.\nExpected: '$o'\nObserved: '$e'\n" }
}

def read_next_evaluation(BufferedReader evalb_reader) {
    def evalb_line = evalb_reader.readLine()

    if (evalb_line.startsWith("===")) return []

    def eval_matcher = evalb_line =~ /\s*(\d+)\s+(\d+)\s+(\d+)\s+([.\d]+)\s+([.\d]+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+([.\d]+)/
    def eval_match = eval_matcher[0]
    def (__, sentence_id1, sent_len, status, recall, precision, matched_bracket, brackets_gold, brackets_test, cross_bracket, word_count, correct_tags, tag_accuracy) = eval_match

    sentence_id1 = sentence_id1 as Integer
    sent_len = sent_len as Integer

    precision = precision as Double
    recall = recall as Double
    tag_accuracy = tag_accuracy as Double

    Double f_measure = (precision + recall) ? 2 * (precision * recall) / (precision + recall) : 0

    [f_measure, sentence_id1, sent_len, status, recall, precision, matched_bracket, brackets_gold, brackets_test, cross_bracket, word_count, correct_tags, tag_accuracy]
}