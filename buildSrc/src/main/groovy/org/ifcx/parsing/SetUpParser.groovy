package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class SetUpParser extends DefaultTask {
    @InputDirectory
    File base_parser_dir

    @Input
    String corpus_name

//    @OutputDirectory
    File parser_dir

    @OutputFile
    File train_all_mrg

    @OutputFile
    File tune_all_mrg

    @TaskAction
    void setup()
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

//        train_all_mrg = new File(parser_dir, "tmp/train-all.mrg")
//        tune_all_mrg = new File(parser_dir, "tmp/tune-all.mrg")

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

//            copy(todir:parser_dir, preservelastmodified:true) {
//                fileset(dir:base_parser_dir) {
//                    include(name:'first-stage/**')
//                    include(name:'second-stage/**')
//                    include(name:'evalb/**')
//                    include(name:'Makefile')
//                    include(name:'train_all.sh')
//                    include(name:'train_all.condor')
//                }
//            }

//        def combined_mrg_file = new File(tmpDir, split_name + '-all.mrg')
//
//        switch (split_name) {
//            case 'train':  train_all_mrg = combined_mrg_file ; break
//            case 'tune':  tune_all_mrg = combined_mrg_file ; break
//        }

//        if (!parser_dir.exists()) {
//            if (!parser_dir.mkdirs()) throw new IOException("Failed to create experiment/parser dir $parser_dir")
//        }
//
        // Can't use Gradle's copy task because it won't preserve timestamps.
//        def copy_base_parser = project.task(name + '-copy-base-parser', type:Copy) {
//            from(base_parser_dir) {
//                exclude 'xtrain/*'
//                exclude 'xtune/*'
//            }
//            destinationDir parser_dir
//        }

//        ["first-stage", "second-stage", "evalb", "Makefile", "train_all.sh", "train_all.condor"].each {
//            def exitCode = copy_files(new File(base_parser_dir, it), new File(parser_dir, it))
//            if (exitCode) throw new IOException("Failed to copy files form $base_parser_dir to $parser_dir (exitCode = $exitCode)")
//        }

//        def corpus_project = project.project(corpus_name)
//
//        ['train', 'tune', 'test'].each { split_name ->
//            def mrg_source_files = corpus_project.tasks[split_name + '_MRG'].outputs.files
//            def dest_dir = new File(parser_dir, 'x' + split_name)
//            def copy_data = project.task("$name-copy-$split_name", type:Copy) {
//                from mrg_source_files
//                destinationDir dest_dir
//            }
//            copy_data.execute()
//        }
    }

//    static def copy_files(File src_dir, File dst_dir)
//    {
//        String cmd = "cp -RLp $src_dir $dst_dir"
//        def proc = cmd.execute()
//        if (proc.waitFor()) {
//            print "Error: '$cmd' = ${proc.exitValue()}"
//        }
//        proc.exitValue()
//    }

}

