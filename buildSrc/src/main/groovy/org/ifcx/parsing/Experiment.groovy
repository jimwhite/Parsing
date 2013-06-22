package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException

class Experiment extends DefaultTask {
    @InputDirectory
    File base_parser_dir

    @Input
    String corpus_name

    @OutputDirectory
    File experiment_dir

    @TaskAction
    void create()
    {
        def parser_dir = new File(experiment_dir, base_parser_dir.name)

        if (!parser_dir.exists()) {
            if (!parser_dir.mkdirs()) throw new IOException("Failed to create experiment/parser dir $parser_dir")
        }

        // Can't use Gradle's copy task because it won't preserve timestamps.
//        def copy_base_parser = project.task(name + '-copy-base-parser', type:Copy) {
//            from(base_parser_dir) {
//                exclude 'xtrain/*'
//                exclude 'xtune/*'
//            }
//            destinationDir parser_dir
//        }

        ["first-stage", "second-stage", "evalb", "Makefile", "train_all.sh", "train_all.condor"].each {
            def result = copy_files(new File(base_parser_dir, it), new File(parser_dir, it))
            if (result) throw new IOException("Failed to copy files form $base_parser_dir to $parser_dir")
        }

        def corpus_project = project.project(corpus_name)

        ['train', 'tune', 'test'].each { split_name ->
            def mrg_source_files = corpus_project.tasks[split_name + '_MRG'].outputs.files
            def dest_dir = new File(parser_dir, 'x' + split_name)
            def copy_data = project.task("$name-copy-$split_name", type:Copy) {
                from mrg_source_files
                destinationDir dest_dir
            }
            copy_data.execute()
        }
    }

    static def copy_files(File src_dir, File dst_dir)
    {
        String cmd = "cp -RLp $src_dir $dst_dir"
        def proc = cmd.execute()
        if (proc.waitFor()) {
            print "Error: '$cmd' = ${proc.exitValue()}"
        }
        proc.exitValue()
    }

}

