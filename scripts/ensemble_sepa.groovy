#!/usr/bin/env groovy

workspace_dir = new File('/home2/jimwhite/workspace/parsers')

mrg_file_name = 'brown-train.mrg'

// Each parser has it's own binary, but we'll use the one in base for them all.
bllip_dir = new File(workspace_dir, 'base/bllip-parser')

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

