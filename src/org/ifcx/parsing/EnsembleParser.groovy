package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory

class EnsembleParser
{
    final Project ensemble_project

    String corpus_name

    String split_method

    Integer split_percentage = 40

    File bllip_parser_dir

    // The list of parsers is kept in sorted order (by project name) so that the corpus splits will match up repeatably.
    List<Project> parser_projects
    List<CharniakParser> parsers

    Task setUpTask

    Task trainTask

    // Number of sentences per batch (which we use rather than articles/documents).
    // This assumes that the source corpus is ordered in a domain and document sensitive way.
    def batch_size = 10

    EnsembleParser(Project p) {
        ensemble_project = p
    }

    def createTasks()
    {
        ensemble_project.with {
//            evaluationDependsOnChildren()
//            beforeEvaluate { println "beforeEvaluate $it" }
//            afterEvaluate { println "afterEvaluate $it" }

            mkdir projectDir
            // Creating the setUpTask also

            // Use closure owner to dodge Gradle's (deprecated) automatic dynamic property creation.
            owner.setUpTask = task(type:SetUpTask, "set_up").configure(SetUpTask.configurer(this))

            // Create the parsers after the ensemble set_up task since they depend on it.
            owner.parser_projects = childProjects.keySet().grep { it.startsWith 'parser_' }.sort().collect { childProjects[it] }
            owner.parsers = parser_projects.collect { create_parser it }

//            parser_projects.each {
//                it.beforeEvaluate { println "beforeEvaluate $it" }
//                it.afterEvaluate { println "afterEvaluate $it" }
//            }

            // Ensemble training is just making sure the training task for each of the child parsers has been done.
            owner.trainTask = project.task("train").configure {
                // The Task.dependsOn method accepts var args for the dependencies, so we spread a list into args.
                // Can't leave the parens out here because then the parser thinks it is multiply instead of spread.
                dependsOn(*parsers.trainTask)
            }

            [set_up:setUpTask, train:trainTask]
        }
    }

    def createTasksForCorpus(String base_name, Task source_task)
    {
        def convert_to_gold = ensemble_project.task("$base_name-convert_to_gold", type:ConvertManyPTB).configure {
            dependsOn source_task
            bllip_parser_dir = owner.bllip_parser_dir
            source = source_task.outputs.files
            output_dir = project.file("$base_name-gold")
            mode = '-e'

            assert bllip_parser_dir
        }

        def convert_to_sent = ensemble_project.task("$base_name-convert_to_sent", type:ConvertManyPTB).configure {
            dependsOn source_task
            bllip_parser_dir = owner.bllip_parser_dir
            source = source_task.outputs.files
            output_dir = project.file("$base_name-sentences")
            mode = '-c'
        }

        // Create the tasks for this corpus in each of our child parsers.
        def parsers_for_corpus = parsers.collect { it.createTasksForCorpus base_name, source_task, true }

        def parse = ensemble_project.task("$base_name-parse").configure {
            dependsOn(*parsers_for_corpus.parse)
        }

        def select_best_parse = ensemble_project.task("$base_name-select", type:SelectParseTask).configure {
            dependsOn(*(parsers_for_corpus.select))
            evalb_program_dir = new File(owner.bllip_parser_dir, 'evalb')
            input_tasks = parsers_for_corpus.select
            best_parse_dir = project.file("$base_name-parsed")
        }

        def evaluate_parse = ensemble_project.task("$base_name-evaluate", type:CharniakParser.EvalBTask).configure {
            evalb_program_dir = new File(owner.bllip_parser_dir, 'evalb')
            gold_task_name = convert_to_gold.name
            input_task_name = select_best_parse.name
            evalb_output_dir = project.file("$base_name-parsed")
        }

        def sepa_filter = ensemble_project.task("$base_name-filter", type:SEPAEvalTask).configure {
            sepa_filter_value = 94
            gold_task = convert_to_gold
            input_task = select_best_parse
            sepa_eval_dir = project.file("$base_name-sepa")
            evalb_program_dir = new File(owner.bllip_parser_dir, 'evalb')
        }

        [convert_to_gold:convert_to_gold, convert_to_sent:convert_to_sent
                , parsers_for_corpus:parsers_for_corpus
                , parse:parse, select:select_best_parse, evaluate:evaluate_parse
                , sepa_filter:sepa_filter
        ]
    }

    protected CharniakParser create_parser(Project parser_project) {
        parser_project.project('corpus').task('split_MRG').configure {
            dependsOn setUpTask

            doFirst {
                project.mkdir project.projectDir
            }

            project.afterEvaluate {
                def parser_i = parser_projects.indexOf(parser_project)

                assert parser_i >= 0

                // Choose split_percentage of the available splits in a range that is centered over this parser's index.
                List files = setUpTask.outputs.files as List
                def split_N = files.size()
                int split_span = (((split_N * split_percentage) / 100) - 1) / 2
                def our_splits = ((parser_i + split_N) - split_span) .. ((parser_i + split_N) + split_span)
                // Groovy lets us get a list of things from a list by supplying a list of indicies.
                // To make wrap-around of the indicies convenient we use a tripled (repeated) list of the split files.
                List<File> our_split_files = (files * 3)[our_splits]

                inputs.files(*our_split_files)

                def train_file = new File(project.projectDir, 'train.mrg')
                def tune_file = new File(project.projectDir, 'tune.mrg')

                outputs.files(train_file, tune_file)

                doLast {
                    train_file.withPrintWriter { train_writer ->
                        tune_file.withPrintWriter { tune_writer ->
                            int lineNumber = 0
                            our_split_files.each {
                                it.eachLine { line ->
                                    if (++lineNumber % 10 == 0) {
                                        tune_writer.println line
                                    } else {
                                        train_writer.println line
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        parser_project.project('corpus').task('train_MRG').configure {
            dependsOn 'split_MRG'
            outputs.file('train.mrg')
        }

        parser_project.project('corpus').task('tune_MRG').configure {
            dependsOn 'split_MRG'
            outputs.file('tune.mrg')
        }

        new CharniakParser(parser_project).with {
            base_parser_dir = owner.bllip_parser_dir
            corpus_name = 'corpus'
            createTasks()
            it
        }
    }

    static class SEPAEvalTask extends DefaultTaskWithEvaluate
    {
        Task input_task

        Task gold_task

        @Input
        Double sepa_filter_value

        @InputDirectory
        File evalb_program_dir

        @OutputDirectory
        File sepa_eval_dir

        File filtered_parses_file

        def sepa_filter(String sepa_line)
        {
            def fields = sepa_line.split(/\s+/)
            def sepa_mean_f = fields[0] as Double

            // len <= 40
//            (fields[3] as Integer) <= 40

            sepa_mean_f > sepa_filter_value
        }

        SEPAEvalTask()
        {
            project.afterEvaluate {
                dependsOn(input_task, gold_task)

                evaluateAfterAll([input_task, gold_task]) {
                    doFirst {
                        project.mkdir sepa_eval_dir
                    }

                    def parse_file = input_task.outputs.files.singleFile
                    def gold_file = gold_task.outputs.files.singleFile
                    def sepa_file = new File(parse_file.parentFile, parse_file.name + '.sepa')

                    inputs.files(parse_file, sepa_file, gold_file)

                    def filtered_sepa_file = new File(sepa_eval_dir, sepa_file.name)
                    def filtered_gold_file = new File(sepa_eval_dir, gold_file.name)
                    def filtered_parse_file = new File(sepa_eval_dir, parse_file.name)

                    def evalb_sepa_filtered = new File(sepa_eval_dir, parse_file.name + '.evalb')

                    outputs.files(filtered_sepa_file, filtered_gold_file, filtered_parse_file, evalb_sepa_filtered)

                    thisObject.filtered_parses_file = filtered_parse_file

                    doLast {
                        sepa_file.withReader { sepa_reader ->
                        gold_file.withReader { gold_reader ->
                        parse_file.withReader { parse_reader ->
                        filtered_sepa_file.withPrintWriter { filtered_sepa_writer ->
                        filtered_gold_file.withPrintWriter { filtered_gold_writer ->
                        filtered_parse_file.withPrintWriter { filtered_parse_writer ->
                            String sepa_line
                            while (sepa_line = sepa_reader.readLine()) {
                                def gold_line = gold_reader.readLine()
                                def parse_line = parse_reader.readLine()

                                if (sepa_filter(sepa_line)) {
                                    filtered_sepa_writer.println(sepa_line)
                                    filtered_gold_writer.println(gold_line)
                                    filtered_parse_writer.println(parse_line)
                                }
                            }
                        }
                        }
                        }
                        }
                        }
                        }

                        def evalb_executable = new File(evalb_program_dir, 'evalb')

                        def evalb_prm_file = new File(evalb_program_dir, 'new.prm')

                        ant.exec(executable: evalb_executable
                                , dir:sepa_eval_dir, failonerror:true
                                , output: evalb_sepa_filtered
                                , error: new File(sepa_eval_dir, evalb_sepa_filtered.name + '.err')) {
                            arg(value:'-p')
                            arg(file: evalb_prm_file)
                            arg(file:filtered_gold_file)
                            arg(file:filtered_parse_file)
                        }
                    }

                    evaluated()
                }
            }
        }
    }

    static class SetUpTask extends DefaultTask
    {
        @Input
        String corpus_name

        @Input
        String split_method

        EnsembleParser ensemble

        def use_task = { String path, String name ->
            def t = project.project(path).tasks[name]
            dependsOn t
            t
        }

        static Closure configurer(EnsembleParser parser)
        {
            return {
                ensemble = parser

                corpus_name = parser.corpus_name
                split_method = parser.split_method

                dependsOn corpus_name + ':train_MRG'
                dependsOn corpus_name + ':tune_MRG'
            }
        }

        SetUpTask()
        {
//            ensemble.parsers.each { it.tasks.set_up.dependsOn setUpTask }

            project.afterEvaluate {
                def train_MRG = use_task(corpus_name, 'train_MRG')
                def tune_MRG = use_task(corpus_name, 'tune_MRG')

                def tmpDir = new File(project.projectDir, 'tmp')

                doFirst {
                    ant.mkdir(dir:tmpDir)
                }

                FileCollection all_mrg_files = train_MRG.outputs.files + tune_MRG.outputs.files

                inputs.files.add(all_mrg_files)

                def combined_mrg_file = new File(tmpDir, 'train-tune-all.mrg')
//                def one_per_line_mrg_file = new File(tmpDir, 'train-tune-all-one-per-line.mrg')

//                outputs.files(combined_mrg_file)
//                outputs.files(one_per_line_mrg_file)

                doLast {
                    ant.concat(destfile: combined_mrg_file, eol:'unix') {
                        // Would like to say this something like this way:
                        // resources(all_mrg_files)
                        all_mrg_files.addToAntBuilder(ant, 'resources')
                    }

//                    // Flatten the parse trees (s-expressions) so that they are one per line.
//                    one_per_line_mrg_file.withPrintWriter { mrg_writer ->
//                    combined_mrg_file.withReader { mrg_reader ->
//                        def sexp
//                        while (sexp = read_one_sexp(mrg_reader)) {
//                            mrg_writer.println sexp_to_string(sexp)
//                        }
//                    }
//                    }
                }

                def split_K = ensemble.parsers.size()

                def splits_dir = new File(ensemble.ensemble_project.projectDir, 'splits')

                doFirst {
                    ant.mkdir(dir:splits_dir)
                }

                List<File> split_files = (0..<split_K).collect { i -> new File(splits_dir, "split_${i}.mrg") }

                outputs.files(*split_files)

                // Flatten the parse trees (s-expressions) so that they are one per line.

                doLast {
                    List<PrintWriter> split_writers = split_files.collect { it.newPrintWriter() }

//                    int[] split_counts = new int[split_K]
//                    int   biggest_split = 0

                    combined_mrg_file.withReader { mrg_reader ->
                        int split_i = 0
                        List<String> batch
                        while (batch = ensemble.read_batch(mrg_reader)) {
                            batch.each { split_writers[split_i].println it }
                            split_i = (split_i + 1) % split_K
                        }
                    }

                    split_writers.each { it.close() }
                }
            }
        }
    }

    static class SelectParseTask extends DefaultTaskWithEvaluate
    {
        List<Task> input_tasks

        @Input
        File best_parse_dir

        @InputDirectory
        File evalb_program_dir

        SelectParseTask() {
            project.afterEvaluate {
                evaluateAfterAll(input_tasks) {
                    def evalb_executable = new File(evalb_program_dir, 'evalb')

                    def evalb_prm_file = new File(evalb_program_dir, 'new.prm')


//                    println "SelectTask.afterEvaluate ${project.path}"
//                    println "input_tasks $input_tasks"
//                    println "select inputs ${input_tasks.inputs.files.files}"

                    doFirst {
                        ant.mkdir(dir:best_parse_dir)
                    }

                    // Make sure our inputs are in some repeatable order.
                    input_tasks = input_tasks.sort { it.project.name }

                    def input_files = input_tasks*.outputs.files.singleFile

                    inputs.files(*input_files)

                    def best_parse_file = new File(best_parse_dir, input_files.first().name)

                    outputs.file(best_parse_file)

//                    println "select outputs"
//                    println outputs.files.files

                    doLast {
//                        ant.copy(file:input_tasks.first().outputs.files.singleFile, tofile:best_parse_file, overwrite:true, force:true)

                        // Some meaningless (but repeatable) seed.
                        def random = new Random(0xbc329e631aL)

                        def best_parse_readers = input_files.collect { it.newReader() }

                        def ensemble_K = best_parse_readers.size()

                        best_parse_file.withPrintWriter { printer ->
                            List<String> parses
                            while ((parses = best_parse_readers.collect { it.readLine() }).every()) {
                                printer.println parses[random.nextInt(ensemble_K)]
                            }
                        }

                        best_parse_readers.each { it.close() }

                        def per_parser_evalb_files = input_tasks.collect { new File(best_parse_dir, it.project.name + '.sepa.evalb') }

                        ensemble_K.times { i ->
                            ant.exec(executable: evalb_executable, dir:best_parse_dir, failonerror:true
                                    , output: per_parser_evalb_files[i]
                                    , error: new File(best_parse_dir, per_parser_evalb_files[i].name + '.err')) {
                                arg(value:'-p')     // Input is tokenized
                                arg(file:evalb_prm_file)
                                arg(file:best_parse_file)
                                arg(file:input_files[i])
                            }
                        }

                        def sepa_file = new File(best_parse_dir,  best_parse_file.name + '.sepa')

                        def evalb_readers = per_parser_evalb_files.collect { evalb_reader(it) }

                        def F_SCORE_FIELD = 0
                        def ID_FIELD = 1
                        def SENT_LEN_FIELD = 2
                        def STATUS_FIELD = 3
                        def TAG_ACC_FIELD = 12

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

                                // We sum all the f-scores but one of them is our "gold" and so will be 100%.
                                def sepa_mean_f = f_scores.sum() / ensemble_K

                                def tag_accuracies = evaluations.collect { it[TAG_ACC_FIELD] }
                                def sepa_tags_mean_f = tag_accuracies.sum() / ensemble_K

                                def len0 = evaluations[0][SENT_LEN_FIELD]
                                def status = evaluations.sum { it[STATUS_FIELD] }

                                printer.println "$sepa_mean_f\t$sepa_tags_mean_f\t$id0\t$len0\t$status\t$ensemble_K\t${f_scores.join('\t')}\t${tag_accuracies.join('\t')}"
                            }
                        }

                        evalb_readers.each { it.close() }
                    }

                    evaluated()
                }
            }
        }
    }

    static BufferedReader evalb_reader(File evalb_file)
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

    static expect_contains(String o, String e)
    {
        if (!o.contains(e)) { println "EvalB header mismatch.\nExpected: '$o'\nObserved: '$e'\n" }
    }

    static read_next_evaluation(BufferedReader evalb_reader) {
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

    List<String> read_batch(Reader reader)
    {
        List<String> batch = []

        def sexp
        while ((batch.size() < batch_size) && (sexp = read_one_sexp(reader))) {
            batch << sexp_to_string(sexp)
        }

        batch.size() == batch_size ? batch : null
    }

    static def read_one_sexp(Reader reader)
    {
        final tokenDelimiters = "()\t\r\n "

        List stack = []
        List sexps = []

        def cint = reader.read()

        loop:
        while (cint >= 0) {
            Character c = cint as Character
            switch (c) {
                case ')' :
                    // End of sexp with without beginning.
                    // Print a warning?
                    if (stack.size() < 1) break loop

                    sexps = stack.pop() << sexps

                    // We read only one complete sexp.
                    if (stack.size() < 1) break loop

                    cint = reader.read()
                    break

                case '(':
                    stack.push(sexps)
                    sexps = []
                    cint = reader.read()
                    break

                default:
                    if (c.isWhitespace() || c == 0 || c == 26 /* ASCII EOF */ ) {
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

        return sexps ? sexps[0] : null
    }

    static def sexp_to_string(sexp)
    {
        (sexp instanceof List) ? "(${sexp.collect { sexp_to_string(it) }.join(' ')})" : sexp.toString()
    }
}
