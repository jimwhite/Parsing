package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input

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
            mkdir projectDir
            // Creating the setUpTask also

            // Use closure owner to dodge Gradle's (deprecated) automatic dynamic property creation.
            owner.setUpTask = task(type:SetUpTask, "set_up").configure(SetUpTask.configurer(this))

            // Create the parsers after the ensemble set_up task since they depend on it.
            owner.parser_projects = childProjects.keySet().grep { it.startsWith 'parser_' }.sort().collect { childProjects[it] }
            owner.parsers = parser_projects.collect { create_parser it }

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

        def parsers_for_corpus = parsers.collect { it.createTasksForCorpus base_name, source_task }

        def parse = ensemble_project.task("$base_name-parse").configure {
            dependsOn(*parsers_for_corpus.parse)
        }

        def select_best_parse = ensemble_project.task("$base_name-select", type:SelectParseTask).configure {
            input_task_name = parse.name
            best_parse_dir = project.file("$base_name-parsed")
        }

        def evaluate_parse = ensemble_project.task("$base_name-evaluate", type:CharniakParser.EvalBTask).configure {
            evalb_program_dir = new File(owner.bllip_parser_dir, 'evalb')
            gold_task_name = convert_to_gold.name
            input_task_name = select_best_parse.name
            evalb_output_dir = project.file("$base_name-parsed")
        }

        [convert_to_gold:convert_to_gold, convert_to_sent:convert_to_sent
                , parsers_for_corpus:parsers_for_corpus
                , parse:parse, select:select_best_parse, evaluate:evaluate_parse]
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

    static class SelectParseTask extends DefaultTask
    {
        @Input
        String input_task_name

        @Input
        File best_parse_dir

        SelectParseTask() {
            project.afterEvaluate {
                Task input_task_task = project.tasks[input_task_name]

                dependsOn input_task_task

                doFirst {
                    ant.mkdir(dir:best_parse_dir)
                }

                input_task_task.outputs.files.each { nbest_file ->
                    def output_file = new File(best_parse_dir, (nbest_file.name - ~/\.sent\.nbest$/) + '.best')

                    inputs.files(nbest_file)
                    outputs.files(output_file)

                    doLast {
                    }
                }
            }
        }
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
