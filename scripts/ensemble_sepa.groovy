#!/usr/bin/env groovy

workspace_dir = new File('/home2/jimwhite/workspace/parsers')

mrg_file_name = 'brown-train.mrg'

// Each parser has it's own binary, but we'll use the one in base for them all.
bllip_parser_dir = new File(workspace_dir, 'base/bllip-parser')

ensemble_dir = new File(workspace_dir, 'ensemble')

parser_dir_list = ensemble_dir.listFiles().findAll { it.name =~ /parser_.*/ }

ensemble_K = parser_dir_list.size()

println ensemble_K

best_parse_readers = parser_dir_list.collect { File parser_dir ->
    def sysout_dir = new File(parser_dir, 'tmp/parsed')
    def best_parse_file = new File(sysout_dir, mrg_file_name + '.best')
    best_parse_file.newReader()
}

// Some meaningless seed.
random = new Random(0xbc329e631aL)

selected_best_file = new File(ensemble_dir, mrg_file_name + '.best.1ofE')

selected_best_file.withPrintWriter { printer ->
    List<String> parses
    while ((parses = best_parse_readers.collect { it.readLine() }).any()) {
        printer.println parses[random.nextInt(ensemble_K)]
    }
}

best_parse_readers.each { it.close() }

selected_evalb_gold_file = new File(ensemble_dir, mrg_file_name + '.1ofE.eval')

prepare_ptb('-e', selected_best_file, selected_evalb_gold_file)

parser_dir_list.each { File parser_dir ->
    def sysout_dir = new File(parser_dir, 'tmp/parsed')
    evalb(selected_best_file, new File(sysout_dir, mrg_file_name + '.best'), new File(sysout_dir, mrg_file_name + '.evalb.txt'))
}

def prepare_ptb(String switches, File src_file, File dst_file)
{
    def command = ['second-stage/programs/prepare-data/ptb', switches, src_file]

    println command

    dst_file.withOutputStream { stdout ->
        def stderr = new ByteArrayOutputStream()
        def proc = command.execute(null, bllip_parser_dir)
        proc.waitForProcessOutput(stdout, stderr)
        if (stderr.size()) System.err.println(stderr.toString())
        if (proc.exitValue()) {
            System.err.println "exitValue: ${proc.exitValue()}"
        }
    }
}

def evalb(File evalb_gold, File best_file, File outFile)
{
    def errFile = new File(outFile.parentFile, (outFile.name - ~/\.txt$/)+ '.err')

    def command = ['evalb/evalb', '-p', 'evalb/new.prm', evalb_gold, best_file]

    println command

    outFile.withOutputStream { stdout ->
        errFile.withOutputStream { stderr ->
            def proc = command.execute(null, bllip_parser_dir)
            proc.waitForProcessOutput(stdout, stderr)
            if (proc.exitValue()) {
                println "exitValue: ${proc.exitValue()}"
            }
        }
    }
}
