package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class EnsembleParser
{

    static class SetUpTask extends DefaultTask
    {
        @Input
        String base_parser_name

        @Input
        String corpus_name

        @Input
        String split_method

        SetUpTask()
        {
            project.afterEvaluate {
                def tmpDir = new File(project.projectDir, 'tmp')

                doFirst {
                    ant.mkdir(dir:tmpDir)
                }

                def corpus_tasks = project.project(corpus_name).tasks

                dependsOn corpus_tasks.train_MRG
                dependsOn corpus_tasks.tune_MRG

                FileCollection all_mrg_files = corpus_tasks.train_MRG.outputs.files + corpus_tasks.tune_MRG.outputs.files

                inputs.files.add(all_mrg_files)

                def combined_mrg_file = new File(tmpDir, 'train-tune-all.mrg')

                outputs.files(combined_mrg_file)

                doLast {
//                    ant.concat(destfile: combined_mrg_file, eol:'unix') {
//                        all_mrg_files.each { f ->
//                            filelist(dir:f.parentFile) { file(name:f.name) }
//                        }
//                    }
                    ant.concat(destfile: combined_mrg_file, eol:'unix') {
                        // Would like to say this something like this way:
                        // filelist(files:all_mrg_files.files)
                        all_mrg_files.addToAntBuilder(ant, 'resources')
                    }
                }
            }
        }
    }
}
