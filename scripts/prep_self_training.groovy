#!/usr/bin/env groovy

parsed_source = args[0]
measure = args[1]
endess = args[2]
percentage = args[3] as Integer
file_base = args[4]

assert measure=='uni'
assert (percentage % 10 == 0)

keepers = percentage / 10
period = 10

def xtrain_dir = new File("bllip-parser/xtrain")
xtrain_dir.mkdirs()

def best_file = new File(parsed_source, "tmp/parsed/${file_base}.best")
def xtrain_mrg_file = new File(xtrain_dir, file_base + ".mrg")

int i = keepers
int j = period

xtrain_mrg_file.withPrintWriter { printer ->
    best_file.eachLine { line ->
        if (i-- > 0) {
            printer.println(line.replaceFirst(/^\(S1/, "("))
        }

        if (--j < 1) {
            i = keepers
            j = period
        }
    }
}
