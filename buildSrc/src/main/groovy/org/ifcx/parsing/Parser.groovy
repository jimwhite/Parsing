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

class Parser
{
    Project _project

    String _corpus_name

    File _base_parser_dir

    File _parser_dir

    File _model_dir

    def createTasks()
    {
        _parser_dir = new File(_project.projectDir, _project.name)
        _model_dir = new File(_parser_dir, 'first-stage/DATA/EN')

        def setUpTask = _project.task(type:SetUpParser, "setUp").configure {
            base_parser_dir = _base_parser_dir
            corpus_name = _corpus_name

            parser_dir = _parser_dir

            train_all_mrg = new File(_parser_dir, 'tmp/train-all.mrg')
            tune_all_mrg = new File(_parser_dir, 'tmp/tune-all.mrg')
        }
        setUpTask.dependsOn _corpus_name + ':train_MRG'
        setUpTask.dependsOn _corpus_name + ':tune_MRG'

        def trainTask = _project.task(type:TrainTask, "train") {
            train_all_mrg = setUpTask.train_all_mrg
            tune_all_mrg = setUpTask.tune_all_mrg
        }
        trainTask.parser_dir = _parser_dir
        trainTask.model_dir = _model_dir
        trainTask.dependsOn setUpTask
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

    class SetUpTask extends DefaultTask
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
