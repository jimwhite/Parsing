package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input

class EnsembleParser
{
    Project ensemble_project

    String corpus_name

    String split_method

    // The list of parsers is kept in sorted order so that the corpus splits will match up repeatably.
    List<Project> parsers

    def setUpTask

    // Number of sentences per batch (which we use rather than articles/documents).
    // This assumes that the source corpus is ordered in a domain and document sensitive way.
    def batch_size = 10

    def createTasks()
    {
        ensemble_project.mkdir(ensemble_project.projectDir)

        // Creating the setUpTask also
        setUpTask = ensemble_project.task(type:SetUpTask, "set_up").configure(SetUpTask.configurer(this))

//        trainTask = project.task(type:TrainTask, "train").configure(TrainTask.configurer(this))

        def child_projects = ensemble_project.childProjects
        parsers = child_projects.keySet().grep { it.startsWith 'parser_' }.sort().collect { child_projects[it] }
        parsers.each { create_parser_tasks it }
    }

    protected void create_parser_tasks(Project parser_project) {
        parser_project.project('corpus').task('split_MRG').configure {
            dependsOn setUpTask

            doFirst {
                project.mkdir project.projectDir
            }

            def splits_dir = new File(ensemble_project.projectDir, 'splits')
            def our_split_file = new File(splits_dir, parser_project.name + '.mrg')

            inputs.files(our_split_file)

            def train_file = new File(project.projectDir, 'train.mrg')
            def tune_file = new File(project.projectDir, 'tune.mrg')

            outputs.files(train_file, tune_file)

            doLast {
                train_file.withPrintWriter { train_writer ->
                tune_file.withPrintWriter { tune_writer ->
                    int lineNumber = 0
                    our_split_file.eachLine { line ->
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

        parser_project.project('corpus').task('train_MRG').configure {
            dependsOn 'split_MRG'
            outputs.file('train.mrg')
        }

        parser_project.project('corpus').task('tune_MRG').configure {
            dependsOn 'split_MRG'
            outputs.file('tune.mrg')
        }

        new CharniakParser().with {
            project = parser_project
            base_parser_dir = new File(ensemble_project.parent.projectDir, 'bllip-parser')
            corpus_name = 'corpus'
            createTasks()
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

                List<File> split_files = ensemble.parsers.collect { new File(splits_dir, it.name + '.mrg') }

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
