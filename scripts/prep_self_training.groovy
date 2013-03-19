#!/usr/bin/env groovy

parsed_source = args[0]
measure = args[1]
endness = args[2]
percentage = args[3] as Integer
file_base = args[4]

assert (endness in ["hi", "lo"])

xtrain_dir = new File("bllip-parser/xtrain")
xtrain_dir.mkdirs()

def best_file = new File(parsed_source, "tmp/parsed/${file_base}.best")
def xtrain_mrg_file = new File(xtrain_dir, file_base + ".mrg")

def stats_file = new File(parsed_source, "tmp/evalb/${file_base}.logp.txt")

def fields = [fm:14, we:16]

switch (measure) {
    case "uni" :
        assert (percentage % 10 == 0)

        def keepers = percentage / 10
        def period = 10

        int i = keepers
        int j = period

        xtrain_mrg_file.withPrintWriter { printer ->
            best_file.eachLine { line ->
                if (i-- > 0) {
                    printer.println(parsed_to_mrg(line))
                }

                if (--j < 1) {
                    i = keepers
                    j = period
                }
            }
        }
        break

    case "fm" :
    case "we" :
        def field_idx = fields[measure]

        def stats = stats_file.readLines().collect { it.split('\t').collect { it as Double } }

        stats.sort(true) { endness == "lo" ? it[field_idx] : -it[field_idx] }

        def keeper_count = Math.round(stats.size() * (percentage / (100 as float))) as Integer

        def keeper_stats = stats.take(keeper_count)

        Set<Integer> keeper_ids = keeper_stats.collect { it[0] as Integer } as Set

        keeper_stats.take(12).each { println it }
        println()
        keeper_stats.reverse().take(12).each { println it }

        xtrain_mrg_file.withPrintWriter { printer ->
            best_file.eachLine { String line, Integer counter ->
                if (keeper_ids.contains(counter)) {
                    printer.println(parsed_to_mrg(line))
                }
            }
        }
        break
    default:
        System.err.println "Unknown measure $measure"
        System.exit(1)
}

private String parsed_to_mrg(String line) {
    line.replaceFirst(/^\(S1/, "(")
}
