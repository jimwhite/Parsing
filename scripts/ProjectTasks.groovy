import org.ifcx.parsing.CharniakParser
import org.ifcx.parsing.CharniakParser.EvalBTask
import org.ifcx.parsing.ConvertManyPTB
import org.ifcx.parsing.DefaultTaskWithEvaluate
import org.ifcx.parsing.EnsembleParser
import org.ifcx.parsing.SetUpParser
import org.ifcx.parsing.SourceTaskWithEvaluate

evaluationDependsOn(':corpora')
evaluationDependsOn(':corpora:anydomain')
evaluationDependsOn(':corpora:anydomain:WSJ')

//println project
//println project(':corpora:anydomain:WSJ')
//println project(':corpora:anydomain:WSJ').tasks

apply plugin: 'base'

//apply from: 'converters.gradle'

project('wsj_only') {
    new CharniakParser(it).with {
        corpus_name = ':corpora:anydomain:WSJ'
        base_parser_dir = project.rootProject.file('bllip-parser')
        createTasks()

        task set_up_reranker {
            dependsOn train  // Could be set_up, but no need to take chances for now.

            def reranker_dir = new File(project.projectDir, "reranker")

            if (reranker_dir.exists()) {
//                throw new Exception("Can't do 'cp -R' over existing reranker dir on Linux ${project}")
            }

            def train_mrg_dir = new File(reranker_dir, 'xtrain')
            def tune_mrg_dir = new File(reranker_dir, 'xtune')

            outputs.dir(train_mrg_dir)
            outputs.dir(tune_mrg_dir)

            doLast {
                // Don't try to use AntBuilder mkdir since Gradle's project.mkdir likes it first.
                mkdir reranker_dir
                mkdir train_mrg_dir
                mkdir tune_mrg_dir

                ant.sequential {
                    exec(executable:'cp', failonerror:true, logError:true) {
                        arg(line:'-RLp')
                        ['second-stage', 'evalb', 'Makefile', 'train_reranker.sh', 'train_reranker.condor'].each {
                            arg(file:new File(base_parser_dir, it))
                        }
                        arg(file:reranker_dir)
                    }

                    // Use the first-stage we already have.
                    exec(executable:'ln', failonerror:true, logError:true) {
                        arg(value:'-s')
                        arg(file:new File(project.projectDir, 'first-stage'))
                        arg(file:new File(reranker_dir, 'first-stage'))
                    }

                    copy(todir:train_mrg_dir, preservelastmodified:true) {
                        fileset(dir:new File(project.projectDir, 'train'))
                    }

                    copy(todir:tune_mrg_dir, preservelastmodified:true) {
                        fileset(dir:new File(project.projectDir, 'tune'))
                    }
                }
            }
        }

        createTasksForCorpus('wsj_test', project(':corpora:anydomain:WSJ').tasks.test_MRG)
        createTasksForCorpus('wsj_tune', project(':corpora:anydomain:WSJ').tasks.tune_MRG)
        createTasksForCorpus('brown_test', project(':corpora:anydomain:Brown').tasks.test_MRG)
        def brown_tune = createTasksForCorpus('brown_tune', project(':corpora:anydomain:Brown').tasks.tune_MRG)
        createTasksForCorpus('brown_train', project(':corpora:anydomain:Brown').tasks.train_MRG)

        task (type:DefaultTaskWithEvaluate, 'brown_tune-rerank') {
            ext.input_task = brown_tune.parse
            ext.reranked_dir = new File(project.projectDir, 'brown_tune-reranks')
            dependsOn input_task
            project.afterEvaluate {
                evaluateAfterAll([input_task]) {
                    def reranker_dir = new File(project.projectDir, 'reranker')
                    def rerankerExecutable = new File(reranker_dir, 'second-stage/programs/features/best-parses')

                    project.mkdir reranked_dir

                    input_task.outputs.files.each { nbest_file ->
                        def output_file = new File(reranked_dir, (nbest_file.name - ~/\.sent\.nbest$/) + '.best')

                        // Must send stderr somewhere since redirecting stdout without a redirect
                        // for stderr will merge them into the output file.
                        def error_file = new File(reranked_dir, output_file.name + '.err')

                        inputs.file(nbest_file)
                        outputs.file(output_file)

                        doLast {
                            def MODELDIR=new File(reranker_dir, 'second-stage/models/ec50spfinal')
                            def ESTIMATORNICKNAME='cvlm-l1c10P1'
                            def RERANKER_WEIGHTS = new File(MODELDIR, ESTIMATORNICKNAME + '-weights.gz')
                            def RERANKER_FEATURES = new File(MODELDIR, 'features.gz')

                            ant.exec(executable: rerankerExecutable, dir:reranked_dir, failonerror:true
                                    , input:nbest_file, output: output_file, error: error_file, logError:true) {
                                arg(value:'-l')
                                arg(file:RERANKER_FEATURES)
                                arg(file:RERANKER_WEIGHTS)
                            }
                        }
                    }
                }

                evaluated()
            }
        }

        task (type:EvalBTask, 'brown_tune-rerank-evaluate') {
            dependsOn 'brown_tune-rerank'
            evalb_program_dir = new File(project.rootProject.file('bllip-parser'), 'evalb')
            input_task_name = 'brown_tune-rerank'
            gold_task_name = 'brown_tune-convert_to_gold'
            evalb_output_dir = new File(project.projectDir, 'reranked')
        }

    }
}

project('ensemble_wsj_uni') {
    new EnsembleParser(it).with {
        bllip_parser_dir = project.rootProject.file('bllip-parser')
        corpus_name = ':corpora:anydomain:WSJ'
        split_method =  'uni'
        createTasks()
        createTasksForCorpus('wsj_test', project(':corpora:anydomain:WSJ').tasks.test_MRG)
        createTasksForCorpus('wsj_tune', project(':corpora:anydomain:WSJ').tasks.tune_MRG)
        createTasksForCorpus('brown_test', project(':corpora:anydomain:Brown').tasks.test_MRG)
        createTasksForCorpus('brown_tune', project(':corpora:anydomain:Brown').tasks.tune_MRG)
        createTasksForCorpus('brown_train', project(':corpora:anydomain:Brown').tasks.train_MRG)
    }

    task clean_setup << {
        ant.delete {
            set_up.outputs.files.addToAntBuilder(ant, 'resources')
        }
    }

    task list_parsers << {
        childProjects.sort { it.key }.each { println it }
    }
}

project('brown_st_wsj_sepa') {
    // Brown self-training adding SEPA filtered data from WSJ parser to WSJ corpus.
    evaluationDependsOn ':ensemble_wsj_uni'

    project('corpus') {
        def check_output_files = { it.each { mrg_file -> if (!mrg_file.exists()) throw new FileNotFoundException("Missing corpus file: $mrg_file") } }

        task split_MRG(type:DefaultTaskWithEvaluate) {
            ext.train_file = new File(project.projectDir, 'st-train.mrg')
            ext.tune_file = new File(project.projectDir, 'st-tune.mrg')
            ext.weight_multiplier = 1

            def input_task = project(':ensemble_wsj_uni').tasks['brown_train-filter']

//            outputs.files train_file, tune_file
//
//            doFirst { mkdir(project.projectDir) }
//
//                def input_task = project(':ensemble_wsj_uni').tasks['brown_train-filter']
//
//                inputs.property('weight_multiplier', weight_multiplier)
//
//                evaluateAfterAll([input_task]) {
//                    dependsOn input_task
//                    def parses_file = input_task.filtered_parses_file
//                    inputs.file parses_file
//                    doLast {
//                        train_file.withPrintWriter { train_writer ->
//                        tune_file.withPrintWriter { tune_writer ->
//                            def i = 0
//                            parses_file.eachLine { line ->
//                                if ((++i % 10) == 0) {
//                                    weight_multiplier.times { tune_writer.println line }
//                                } else {
//                                    weight_multiplier.times { train_writer.println line }
//                                }
//                            }
//                        }
//                        }
//                    }
//                    evaluated()
//                }
            outputs.files train_file, tune_file

            doFirst { mkdir(project.projectDir) }

            inputs.property('weight_multiplier', weight_multiplier)

            evaluateAfterAll([input_task]) {
                dependsOn input_task
                def parses_file = input_task.filtered_parses_file
                inputs.file parses_file

                println train_file
                println tune_file

                doLast {
                    train_file.withPrintWriter { train_writer ->
                        tune_file.withPrintWriter { tune_writer ->
                            def i = 0
                            parses_file.eachLine { line ->
                                if ((++i % 10) == 0) {
                                    weight_multiplier.times { tune_writer.println line }
                                } else {
                                    weight_multiplier.times { train_writer.println line }
                                }
                            }
                        }
                    }
                }
                evaluated()
            }
        }

        task train_MRG(type:DefaultTaskWithEvaluate) {
            dependsOn 'split_MRG'
            inputs.files(project(':corpora:anydomain:WSJ').tasks.train_MRG.outputs.files)
            inputs.file split_MRG.train_file
            outputs.files(project(':corpora:anydomain:WSJ').tasks.train_MRG.outputs.files)
            outputs.file split_MRG.train_file
            doLast { check_output_files(outputs.files) }
        }

//        task tune_MRG {
//            dependsOn 'split_MRG'
////            inputs.files(project(':corpora:anydomain:WSJ').tasks.tune_MRG.outputs.files)
//            inputs.file split_MRG.tune_file
////            outputs.files(project(':corpora:anydomain:WSJ').tasks.tune_MRG.outputs.files)
//            outputs.file split_MRG.tune_file
//            doFirst { check_output_files(outputs.files) }
//        }

        task tune_MRG(type:DefaultTaskWithEvaluate) {
//            dependsOn 'split_MRG'
//            inputs.files(project(':corpora:anydomain:WSJ').tasks.tune_MRG.outputs.files)
//            inputs.file split_MRG.tune_file
//            def merged_tune_file = new File(project.projectDir, 'st-tune-merged.mrg')
//            outputs.file merged_tune_file
//            doLast {
//                println "merging"
//                ant.concat(destfile: merged_tune_file, eol:'unix') {
//                    [split_MRG.tune_file, project(':corpora:anydomain:WSJ').tasks.tune_MRG.outputs.files.singleFile].each { f ->
//                        println f
//                        filelist(dir:f.parentFile) { file(name:f.name) }
//                    }
//                }
//            }
            dependsOn 'split_MRG'
//            inputs.file split_MRG.tune_file
            inputs.files(project(':corpora:anydomain:WSJ').tasks.tune_MRG.outputs.files)
            def merged_tune_file = new File(project.projectDir, 'st-tune-merged.mrg')
//            println "Will merge to $merged_tune_file from ${split_MRG.tune_file}"
            outputs.file merged_tune_file
            evaluateAfterAll([split_MRG]) {
                doLast {
                    ant.concat(destfile: merged_tune_file.path, eol:'unix') {
                        inputs.files.addToAntBuilder(ant, 'resources')
                    }
                }
                evaluated()
            }
        }

        task list {
            [train_MRG, tune_MRG].each { splitTask ->
                dependsOn splitTask
                doLast { splitTask.outputs.files.each { println it } }
            }
        }
    }

    new CharniakParser(it).with {
        corpus_name = 'corpus'
        base_parser_dir = project.rootProject.file('bllip-parser')
        createTasks()
        createTasksForCorpus('wsj_test', project(':corpora:anydomain:WSJ').tasks.test_MRG)
        createTasksForCorpus('wsj_tune', project(':corpora:anydomain:WSJ').tasks.tune_MRG)
        createTasksForCorpus('brown_test', project(':corpora:anydomain:Brown').tasks.test_MRG)
        createTasksForCorpus('brown_tune', project(':corpora:anydomain:Brown').tasks.tune_MRG)
        createTasksForCorpus('brown_train', project(':corpora:anydomain:Brown').tasks.train_MRG)
    }
}

project('brown_st_wsj') {
    // Brown self-training adding SEPA filtered data from WSJ parser to WSJ corpus.
    evaluationDependsOn ':wsj_only'
    evaluationDependsOn 'corpus'

    project('corpus') {
        def check_output_files = { it.each { mrg_file -> if (!mrg_file.exists()) throw new FileNotFoundException("Missing corpus file: $mrg_file") } }

        task split_MRG(type:DefaultTaskWithEvaluate) {
            ext.train_file = new File(project.projectDir, 'st-train.mrg')
            ext.tune_file = new File(project.projectDir, 'st-tune.mrg')
            ext.weight_multiplier = 1

            outputs.files train_file, tune_file

            doFirst { println "mkdir" ; mkdir(project.projectDir) }

            def input_task = project(':wsj_only').tasks['brown_train-select']

            inputs.property('weight_multiplier', weight_multiplier)

            evaluateAfterAll([input_task]) {
                dependsOn input_task
                def parses_file = input_task.outputs.files.singleFile
                inputs.file parses_file

                doLast {
                    train_file.withPrintWriter { train_writer ->
                        tune_file.withPrintWriter { tune_writer ->
                            def i = 0
                            parses_file.eachLine { line ->
                                if ((++i % 10) == 0) {
                                    weight_multiplier.times { tune_writer.println line }
                                } else {
                                    weight_multiplier.times { train_writer.println line }
                                }
                            }
                        }
                    }
                }
                evaluated()
            }
        }

        task train_MRG(type:DefaultTaskWithEvaluate) {
            dependsOn 'split_MRG'
            inputs.files(project(':corpora:anydomain:WSJ').tasks.train_MRG.outputs.files)
            inputs.file split_MRG.train_file
            outputs.files(project(':corpora:anydomain:WSJ').tasks.train_MRG.outputs.files)
            outputs.file split_MRG.train_file
            evaluateAfterAll([split_MRG]) {
                doLast { println "ck" ; check_output_files(outputs.files) }
                evaluated()
            }
        }

        task tune_MRG(type:DefaultTaskWithEvaluate) {
            dependsOn 'split_MRG'
//            inputs.file split_MRG.tune_file
            inputs.files(project(':corpora:anydomain:WSJ').tasks.tune_MRG.outputs.files)
            def merged_tune_file = new File(project.projectDir, 'st-tune-merged.mrg')
//            println "Will merge to $merged_tune_file from ${split_MRG.tune_file}"
            outputs.file merged_tune_file
            evaluateAfterAll([split_MRG]) {
                println "split_MRG was evaluated"
                doFirst {
                    println "wsj tune only"
                    println ant
                    ant.concat(destfile: merged_tune_file.path, eol:'unix') {
//                        [split_MRG.tune_file, project(':corpora:anydomain:WSJ').tasks.tune_MRG.outputs.files.singleFile].each { f ->
//                            println f
//                            filelist(dir:f.parentFile) { file(name:f.name) }
//                        }
//                        filelist(dir:split_MRG.tune_file.parentFile) { file(name:split_MRG.tune_file.name) }
                        inputs.files.addToAntBuilder(ant, 'resources')
                    }
                }
                evaluated()
            }
            doLast {
                DefaultTaskWithEvaluate.allWithEval.each { if (it.closuresToCall) println "${it.path} ${it.isEvaluated}" }
                SourceTaskWithEvaluate.allWithEval.each { if (it.closuresToCall) println "${it.path} ${it.isEvaluated}" }
            }
        }

        task list {
            [train_MRG, tune_MRG].each { splitTask ->
                dependsOn splitTask
                doLast {
                    splitTask.outputs.files.each { println it }
                }
            }

            doLast {
                DefaultTaskWithEvaluate.allWithEval.each { if (it.closuresToCall) println "${it.path} ${it.isEvaluated}" }
                SourceTaskWithEvaluate.allWithEval.each { if (it.closuresToCall) println "${it.path} ${it.isEvaluated}" }
            }
        }
    }

    new CharniakParser(it).with {
        corpus_name = 'corpus'
        base_parser_dir = project.rootProject.file('bllip-parser')
        createTasks()
        createTasksForCorpus('wsj_test', project(':corpora:anydomain:WSJ').tasks.test_MRG)
        createTasksForCorpus('wsj_tune', project(':corpora:anydomain:WSJ').tasks.tune_MRG)
        createTasksForCorpus('brown_test', project(':corpora:anydomain:Brown').tasks.test_MRG)
        createTasksForCorpus('brown_tune', project(':corpora:anydomain:Brown').tasks.tune_MRG)
        createTasksForCorpus('brown_train', project(':corpora:anydomain:Brown').tasks.train_MRG)
    }
}

task setup_wsj_only (type:SetUpParser) {
    dependsOn 'corpora:anydomain:WSJ:train_MRG'
    dependsOn 'corpora:anydomain:WSJ:tune_MRG'

    base_parser_dir = file('bllip-parser')
    corpus_name = 'corpora:anydomain:WSJ'
    parser_dir = file('wsj_only')
}

//task train_wsj_only(dependsOn:setup_wsj_only) << {
//    ant.exec(executable:'first-stage/TRAIN/allScript', dir:setup_wsj_only.parser_dir, failonerror:true) {
//        arg(file:setup_wsj_only.model_dir)
//        arg(file:setup_wsj_only.train_all_mrg)
//        arg(file:setup_wsj_only.tune_all_mrg)
//    }
//}

task wrapper(type: Wrapper) {
//  gradleVersion = "1.7-20130626220034+0000"
    gradleVersion = "1.8-20130630220025+0000"
}

allprojects {
//     println project
    task show << { t -> logger.warn "project ${project.absoluteProjectPath(t.name)}" }
}

task list_projects << {
    allprojects.each { println it }
}