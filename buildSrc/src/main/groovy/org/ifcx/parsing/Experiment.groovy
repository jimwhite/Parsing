package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

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
        experiment_dir.mkdirs()

        def parser_dir = new File(experiment_dir, base_parser_dir.name)

        def copy_base_parser = project.task(name + '-copy-base-parser', type:Copy) {
            from(base_parser_dir) {
                exclude 'xtrain/*'
                exclude 'xtune/*'
            }
            destinationDir parser_dir
        }
        copy_base_parser.execute()

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
}

