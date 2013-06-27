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
                def one_per_line_mrg_file = new File(tmpDir, 'train-tune-all-one-per-line.mrg')

                outputs.files(combined_mrg_file)
                outputs.files(one_per_line_mrg_file)

                doLast {
                    ant.concat(destfile: combined_mrg_file, eol:'unix') {
                        // Would like to say this something like this way:
                        // resources(all_mrg_files.files)
                        all_mrg_files.addToAntBuilder(ant, 'resources')
                    }

                    // Flatten the parse trees (s-expressions) so that they are one per line.
                    one_per_line_mrg_file.withPrintWriter { mrg_writer ->
                    combined_mrg_file.withReader { mrg_reader ->
                        def sexp
                        while (sexp = read_one_sexp(mrg_reader)) {
                            mrg_writer.println sexp_to_string(sexp)
                        }
                    }
                    }
                }
            }
        }
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
