#!/usr/bin/env groovy

keepers = args[0] as Integer
skippers = args[1] as Integer

bllip_dir = new File('bllip-parser')

tmp_dir = new File('tmp')

sysout_dir = new File(tmp_dir, 'xcorpus')

all_files = sysout_dir.listFiles().grep { it.isDirectory() }.collectMany { it.listFiles().grep { it.name.endsWith ".best" } }

training_data_dir = new File('xtrain')
training_data_dir.delete()
training_data_dir.mkdirs()

all_files.each { File best ->
    def training_file = new File(training_data_dir, (best.name - ~/\.best$/))
    training_file.withPrintWriter { printer ->
        int i = keepers
        int j = skippers
        best.eachLine { line ->
            if (--i >= 0) {
                printer.println (line.replaceFirst(/^\(S1/, "("))
            } else {
                if (--j < 1) {
                    i = keepers
                    j = skippers
                }
            }
        }
    }
}
