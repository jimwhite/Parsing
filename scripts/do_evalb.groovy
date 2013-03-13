#!/usr/bin/env groovy

bllip_dir = new File('bllip-parser')

tmp_dir = new File('tmp')

sysout_dir = new File(tmp_dir, 'xcorpus')

all_files = sysout_dir.listFiles().grep { it.isDirectory() }.collectMany { it.listFiles().grep { it.name.endsWith ".best" } }

evalb_dir = new File(tmp_dir, 'evalb')

evalb(all_files, new File(evalb_dir, 'xcorpus'))

all_files.each { File best ->
    // evalb([new File(tmp_dir, 'xcorpus/cf/cf03.mrg.best')], new File(evalb_dir, 'cf/cf03'))
    evalb([best], new File(evalb_dir, best.parentFile.name + '/' + (best.name - /\.mrg.best$/)))
}


// bllip-parser/evalb/evalb -p bllip-parser/evalb/new.prm tmp/xcorpus/cf/cf05.mrg.eval tmp/xcorpus/cf/cf05.mrg.best

def evalb(List<File> best_files, File baseFile)
{
    def reranker_output = new File(baseFile.parentFile, baseFile.name + '.best')
    def evalb_gold = new File(baseFile.parentFile, baseFile.name + '.eval')

    baseFile.parentFile.mkdirs()
    reranker_output.delete()
    evalb_gold.delete()

    best_files.each { File best ->
        reranker_output << best.text
        evalb_gold << new File(best.parentFile, best.name.replaceAll(/best$/, /eval/)).text
    }

    def outFile = new File(baseFile.parentFile, baseFile.name + '.evalb.txt')
    def errFile = new File(baseFile.parentFile, baseFile.name + '.err')

    def command = ['bllip-parser/evalb/evalb', '-p', 'bllip-parser/evalb/new.prm', evalb_gold, reranker_output]

    outFile.withOutputStream { stdout ->
        errFile.withOutputStream { stderr ->
            def proc = command.execute()
            proc.consumeProcessOutput(stdout, stderr)
            proc.waitFor()
            if (proc.exitValue()) {
                println "exitValue: ${proc.exitValue()}"
            }
        }
    }
}