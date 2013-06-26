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
    Project project

    String corpus_name

    File base_parser_dir

    File parser_dir

    File model_dir

    Integer number_of_parses = 50

    def setUpTask

    def trainTask

    def createTasks()
    {
        parser_dir = project.projectDir // new File(project.projectDir, project.name)

        model_dir = new File(parser_dir, 'first-stage/DATA/EN')

        setUpTask = project.task(type:SetUpTask, "setUp").configure(SetUpTask.configurer(this))

        trainTask = project.task(type:TrainTask, "train").configure(TrainTask.configurer(this))
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
        String input_task

//        @Input
        CharniakParser parser

        @Input
        File parser_dir

        @Input
        Integer number_of_parses = 50

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

                Task input_task_task = project.tasks[input_task]

                dependsOn input_task_task

                def parserExecutable = new File(parser_dir, 'first-stage/PARSE/parseIt')

                input_task_task.outputs.files.each { sent_file ->
                    def output_file = new File(nbest_parses_dir, sent_file.name + '.nbest')

                    // Must send stderr somewhere since redirecting stdout without a redirect
                    // for stderr will merge them into the output file.
                    def error_file = new File(nbest_parses_dir, sent_file.name + '.err')

                    outputs.files(output_file)

                    doLast {
                        ant.exec(executable: parserExecutable, dir:parser_dir, failonerror:true
                                , output: output_file, error: error_file) {
                            arg(value:'-K')     // Input is tokenized
                            arg(value:"-N$number_of_parses")
                            arg(value:model_dir.path + '/')
                            arg(file:sent_file)
                        }
                    }
                }
            }
        }

        static Closure configure(CharniakParser parser)
        {
            return {
                dependsOn parser.trainTask

                parser_dir = parser.parser_dir
                model_dir = parser.model_dir
                number_of_parses = parser.number_of_parses
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
