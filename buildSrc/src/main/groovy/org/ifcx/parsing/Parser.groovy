package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class Parser
{
    Project project

    String corpus_name

    File base_parser_dir

    File parser_dir

    File model_dir

    def setUpTask

    def trainTask

    def createTasks()
    {
        parser_dir = new File(project.projectDir, project.name)
        model_dir = new File(parser_dir, 'first-stage/DATA/EN')

        setUpTask = project.task(type:SetUpTask, "setUp").configure(SetUpTask.configure(this))

        trainTask = project.task(type:TrainTask, "train").configure(TrainTask.configure(this))
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

        static Closure configure(Parser parser)
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

        static Closure configure(Parser parser)
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
                    ['first-stage', 'second-stage', 'evalb', 'Makefile', 'train_all.sh', 'train_all.condor'].each {
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
