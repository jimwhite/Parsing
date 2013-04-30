#!/usr/bin/env groovy

measure = args[0]
endness = args[1]
percentage = args[2] as Integer
stats_file = new File(args[3])
best_file = new File(args[4])

assert (endness in ["hi", "lo"])

//def best_file = new File(parsed_source, "tmp/parsed/${file_base}.best")
//def stats_file = new File(parsed_source, "tmp/evalb/${file_base}.logp.txt")

def file_base = "${(best_file.name - ~/\..*$/)}.sepa-$measure-$endness-$percentage"

bllip_parser_dir = new File("bllip-parser")

xtrain_dir = new File(bllip_parser_dir, "xtrain")
xtrain_dir.mkdirs()

xtrain_mrg_file = new File(xtrain_dir, file_base + ".train.mrg")

xtune_dir = new File(bllip_parser_dir, "xtune")
xtune_dir.mkdirs()

xtune_mrg_file = new File(xtune_dir, file_base + ".tune.mrg")

def fields = [fm:14, we:16, smfb:0, smft:0, tmfb:0, tmft:1]

def field_idx = fields[measure]

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
        prep_st_methods_fm_we(stats_file, field_idx, endness, percentage, best_file)
        break

    /* Percentage of parses sorted by mean F-score for brackets. */
    case "smfb" :
    /* Percentage of parses sorted by mean F-score for tags. */
    case "smft" :
        prep_st_sorted_sepa(stats_file, field_idx, endness, percentage, best_file)
        break

    /* Threshold parses by mean F-score for brackets. */
    case "tmfb" :
    /* Threshold parses by mean F-score for tags. */
    case "tmft" :

//        break

    default:
        System.err.println "Unknown measure $measure"
        System.exit(1)
}

def prep_st_sorted_sepa(File stats_file, field_idx, endness, int percentage, best_file)
{
    def stats = stats_file.readLines().collect { it.split('\t').collect { it as Double } }

    stats.sort(true) { endness == "lo" ? it[field_idx] : -it[field_idx] }

    def keeper_count = Math.round(stats.size() * (percentage / (100 as float))) as Integer
    
    if (keeper_count < 1) return false
    
    def keeper_stats = stats.take(keeper_count)

    // This is only difference?  ID field is in different column (for now anyhow - maybe I should change it...).
    List<Integer> keeper_ids = keeper_stats.collect { it[2] as Integer }

    keeper_stats.take(12).each { println it }
    println()
    keeper_stats.reverse().take(12).each { println it }

    def kept_count = 0
    def tune_count = 0

    xtrain_mrg_file.withPrintWriter { train_printer ->
    xtune_mrg_file.withPrintWriter { tune_printer ->
        best_file.eachLine { String line, Integer counter ->
            if (keeper_ids.contains(counter)) {
                if (tune_count < 500 && (kept_count % 10 == 9)) {
                    tune_printer.println parsed_to_mrg(line)
                    tune_count += 1
                } else {
                    train_printer.println(parsed_to_mrg(line))
                }
                kept_count += 1
            }
        }
    }
    }
}

def prep_st_methods_fm_we(File stats_file, int field_idx, String endness, int percentage, File best_file)
{
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
}

private String parsed_to_mrg(String line) {
    line.replaceFirst(/^\(S1/, "(")
}
