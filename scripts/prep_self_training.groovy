#!/usr/bin/env groovy

bllip_dir = new File('bllip-parser')

tmp_dir = new File('tmp')

sysout_dir = new File(tmp_dir, 'xcorpus')

all_files = sysout_dir.listFiles().grep { it.isDirectory() }.collectMany { it.listFiles().grep { it.name.endsWith ".best" } }

xcorpus_dir = new File('xcorpus')

training_data_dir = new File(xcorpus_dir, 'train')

training_data_dir.mkdirs()

all_files.each { File best ->
    def training_file = new File(training_data_dir, (best.name - ~/\.best$/))
    training_file.withPrintWriter { printer ->
        best.eachLine { printer.println (it.replaceFirst(/^\(S1/, "(")) }
    }
}
