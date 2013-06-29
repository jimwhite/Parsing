package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class CharniakParser
{
    final Project project

    String corpus_name

    File base_parser_dir

    Integer number_of_parses = 50

    def setUpTask

    def trainTask

    private File parser_dir

    private File model_dir

    CharniakParser(Project project) {
        this.project = project
    }

    def createTasks()
    {
        parser_dir = project.projectDir // new File(project.projectDir, project.name)

        model_dir = new File(parser_dir, 'first-stage/DATA/EN')

        setUpTask = project.task(type:SetUpTask, "set_up").configure(SetUpTask.configurer(this))

        trainTask = project.task(type:TrainTask, "train").configure(TrainTask.configurer(this))

        [set_up:setUpTask, train:trainTask]
    }

    def createTasksForCorpus(String base_name, Task source_task)
    {
        def _parser = this

//        project.mkdir project.file(base_name)

        def parsed_dir = project.file("$base_name-parsed")

        def convert_to_gold = project.task("$base_name-convert_to_gold", type:ConvertManyPTB).configure {
            dependsOn source_task
            bllip_parser_dir = owner.base_parser_dir
            source = source_task.outputs.files
            output_dir = project.file("$base_name-gold")
            mode = '-e'

            assert bllip_parser_dir
        }

        def convert_to_sent = project.task("$base_name-convert_to_sent", type:ConvertManyPTB).configure {
            dependsOn source_task
            bllip_parser_dir = owner.base_parser_dir
            source = source_task.outputs.files
            output_dir = project.file("$base_name-sentences")
            mode = '-c'
        }

        def parse = project.task("$base_name-parse", type:ParseTask).configure {
            parser = _parser
            input_task_name = convert_to_sent.name
            nbest_parses_dir = parsed_dir
        }

        def select_best_parse = project.task("$base_name-select", type:SelectParseTask).configure {
            input_task_name = parse.name
            best_parse_dir = parsed_dir
        }

        def evaluate_parse = project.task("$base_name-evaluate", type:EvalBTask).configure {
            evalb_program_dir = new File(owner.base_parser_dir, 'evalb')
            gold_task_name = convert_to_gold.name
            input_task_name = select_best_parse.name
            evalb_output_dir = parsed_dir
        }

        [convert_to_gold:convert_to_gold, convert_to_sent:convert_to_sent
            , parse:parse, select:select_best_parse, evaluate:evaluate_parse]
    }

    static class TrainTask extends DefaultTask
    {
        @Input
        File parser_dir

        @InputFile
        File train_all_mrg

        @InputFile
        File tune_all_mrg

        @OutputDirectory
        File model_dir

        static Closure configurer(CharniakParser parser)
        {
            return {
                dependsOn parser.setUpTask

                parser_dir = parser.parser_dir
                model_dir = parser.model_dir
                train_all_mrg = parser.setUpTask.train_all_mrg
                tune_all_mrg = parser.setUpTask.tune_all_mrg
            }
        }

        @TaskAction
        void train()
        {
            ant.exec(executable:'first-stage/TRAIN/allScript', dir:parser_dir, failonerror:true) {
                arg(file:model_dir)
                arg(file:train_all_mrg)
                arg(file:tune_all_mrg)
            }
        }
    }

    static class ParseTask extends DefaultTask
    {
        @Input
        String input_task_name

//        @Input
        CharniakParser parser

        @Input
        File parser_dir

        @Input
        Integer number_of_parses

        @InputDirectory
        File model_dir

        @Input
        File nbest_parses_dir

        ParseTask() {
            project.afterEvaluate {
                dependsOn parser.trainTask

                parser_dir = parser.parser_dir
                model_dir = parser.model_dir
                number_of_parses = parser.number_of_parses

                doFirst {
                    ant.mkdir(dir:nbest_parses_dir)
                }

                Task input_task_task = project.tasks[input_task_name]

                dependsOn input_task_task

                def parserExecutable = new File(parser_dir, 'first-stage/PARSE/parseIt')

                input_task_task.outputs.files.each { sent_file ->
                    def output_file = new File(nbest_parses_dir, sent_file.name + '.nbest')

                    // Must send stderr somewhere since redirecting stdout without a redirect
                    // for stderr will merge them into the output file.
                    def error_file = new File(nbest_parses_dir, output_file.name + '.err')

                    outputs.files(output_file)

                    doLast {
                        ant.exec(executable: parserExecutable, dir:parser_dir, failonerror:true
                                , output: output_file, error: error_file) {
                            arg(value:'-K')     // Input is tokenized
                            arg(value:'-l400')  // Accept very long sentences.
                            arg(value:"-N$number_of_parses")
                            arg(value:model_dir.path + '/')
                            arg(file:sent_file)
                        }
                    }
                }
            }
        }

        static Closure configurer(CharniakParser parser)
        {
            return {
                dependsOn parser.trainTask

                parser_dir = parser.parser_dir
                model_dir = parser.model_dir
                number_of_parses = parser.number_of_parses
            }
        }
    }

    static class SelectParseTask extends DefaultTaskWithEvaluate
    {
        @Input
        String input_task_name

        @Input
        File best_parse_dir

        SelectParseTask() {
            project.afterEvaluate {
                println "SelectTask.afterEvaluate  ${project.path} ${it}"

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
                        output_file.withPrintWriter { best_writer ->
                        nbest_file.withReader { nbest_reader ->
                            String line
                            while (line = nbest_reader.readLine()) {
                                def (_, parse_count, sentence_id) = (line =~ /(\d+)[^.]+\.(.+)/)[0]
                                parse_count = parse_count as Integer

                                parse_count.times { i ->
                                    def log_p_parse = nbest_reader.readLine() as Double
                                    def parse = nbest_reader.readLine()

                                    // Output the first (which is the most likely) parse.
                                    if (i == 0) {
                                        best_writer.println parse
                                    }
                                }

                                // Should be a blank separator line.
                                line = nbest_reader.readLine()
                                assert !line.trim()
                            }
                        }
                        }
                    }
                }

                evaluated()
            }
        }
    }

    static class EvalBTask extends DefaultTaskWithEvaluate
    {
        @Input
        String gold_task_name

        @Input
        String input_task_name

        @InputDirectory
        File evalb_program_dir

        @Input
        File evalb_output_dir

        EvalBTask() {
            def _task = this
            project.afterEvaluate {
                Task input_task = project.tasks[input_task_name]

                dependsOn input_task

                Task gold_task = project.tasks[gold_task_name]

                dependsOn gold_task

                def evalb_prm_file = new File(evalb_program_dir, 'new.prm')

                inputs.files(evalb_prm_file)

                evaluateAfterAll([input_task, gold_task]) {
                    println "evaluateAfter $_task"
                    Map<String, File> gold_parse_files = gold_task.outputs.files.files.collectEntries { [it.name, it] }

                    doFirst {
                        ant.mkdir(dir:evalb_output_dir)
                    }

                    def evalb_executable = new File(evalb_program_dir, 'evalb')

                    input_task.outputs.files.each { best_parse_file ->
                        def base_name = best_parse_file.name - ~/\.best$/
                        def gold_parse_file = gold_parse_files[base_name + '.gold']
                        def output_file = new File(evalb_output_dir, base_name + '.evalb')

                        // Must send stderr somewhere since redirecting stdout without a redirect
                        // for stderr will merge them into the output file.
                        def error_file = new File(evalb_output_dir, output_file.name + '.err')

                        println best_parse_file
                        println output_file
                        println gold_parse_file
                        println "yo on file()"

                        inputs.files(gold_parse_file, best_parse_file)
                        outputs.file(output_file)

                        doLast {
                            ant.exec(executable: evalb_executable, dir:evalb_output_dir, failonerror:true
                                    , output: output_file, error: error_file) {
                                arg(value:'-p')     // Input is tokenized
                                arg(file:evalb_prm_file)
                                arg(file:gold_parse_file)
                                arg(file:best_parse_file)
                            }
                        }
                    }
                }

                evaluated()
            }
        }
    }

    static class SetUpTask extends DefaultTask
    {
        @InputDirectory
        File base_parser_dir

        @Input
        String corpus_name

//    @OutputDirectory
        File parser_dir

//        @OutputDirectory
        File model_dir

        @OutputFile
        File train_all_mrg

        @OutputFile
        File tune_all_mrg

        static Closure configurer(CharniakParser parser)
        {
            return {
                corpus_name = parser.corpus_name
                base_parser_dir = parser.base_parser_dir
                parser_dir = parser.parser_dir

                dependsOn corpus_name + ':train_MRG'
                dependsOn corpus_name + ':tune_MRG'

                train_all_mrg = new File(parser_dir, 'tmp/train-all.mrg')
                tune_all_mrg = new File(parser_dir, 'tmp/tune-all.mrg')
            }
        }

        @TaskAction
        void setUp()
        {
            def tmpDir = new File(parser_dir, 'tmp')

            ant.sequential {
                mkdir(dir:parser_dir)

                mkdir(dir:tmpDir)

                exec(executable:'cp') {
                    arg(line:'-RLp')
                    ['first-stage'/*, 'second-stage', 'evalb', 'Makefile', 'train_all.sh', 'train_all.condor'*/].each {
                        arg(file:new File(base_parser_dir, it))
                    }
                    arg(file:parser_dir)
                }
            }

            def corpus_tasks = project.project(corpus_name).tasks

//        [[corpus_tasks.'train_MRG', 'train'], [corpus_tasks.'tune_MRG', 'tune'], [corpus_tasks.'test_MRG', 'test']].each { corpus_task, split_name ->

//            train_all_mrg = new File(parser_dir, "tmp/train-all.mrg")
//            tune_all_mrg = new File(parser_dir, "tmp/tune-all.mrg")

            [['train', train_all_mrg], ['tune', tune_all_mrg] ].each { split_name, combined_mrg_file ->
                def corpus_task = corpus_tasks[split_name + '_MRG']
                def mrg_source_files = corpus_task.outputs.files

                ant.sequential {
                    copy(todir:new File(parser_dir, split_name), preservelastmodified:true) {
                        mrg_source_files.each { f ->
                            filelist(dir:f.parentFile) { file(name:f.name) }
                        }
                    }

                    concat(destfile: combined_mrg_file, eol:'unix') {
                        mrg_source_files.each { f ->
                            filelist(dir:f.parentFile) { file(name:f.name) }
                        }
                    }
                }

                // Too cool for school one-liner:
                // new File(tmpDir, split_name + '-all-files.txt').write(mrg_source_files*.path.join('\n'))

                // The sensible expanded version...
                new File(tmpDir, split_name + '-all-files.txt').withPrintWriter { listing ->
                    mrg_source_files.each { listing.println it.path }
                }
            }
        }
    }
}
