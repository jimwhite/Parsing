#!/bin/env groovy

import edu.stanford.nlp.trees.PennTreeReader
import edu.stanford.nlp.trees.Tree

//@Grapes(
//        @Grab(group='edu.stanford.nlp', module='stanford-corenlp', version='1.3.4')
//)

original_brown_corpus = new File('/corpora/LDC/LDC99T42/RAW/parsed/mrg/brown')

brown_corpus = new File('corpora/brown/mrg')

original_brown_corpus.eachFileRecurse { File original_file ->
    if (original_file.isFile() /*&& (original_file.name == "cf01.mrg")*/) {
        def cleaned_file = new File(brown_corpus, original_file.absolutePath - original_brown_corpus.absolutePath)

//        println cleaned_file.path

        if (!cleaned_file.parentFile.isDirectory()) cleaned_file.parentFile.mkdirs()

//        def lines = original_file.readLines()
//
//        while (lines && lines[0].startsWith("*")) lines.remove(0)
//
//        cleaned_file.write(lines.join('\n'))

        cleaned_file.withPrintWriter { printer ->
            original_file.withReader { Reader reader ->
                PennTreeReader treereader = new PennTreeReader(reader)

                def count = 0

                Tree tree = null

                while (tree = treereader.readTree()) {
                    count += 1
                    def penn = tree.pennString()
                    penn = penn.replaceAll(/^\(null/, "(")
//                    def penn = "( " + tree.firstChild().pennString() + ")"
//                    printer.println (penn)
//                    printer.println()
                    def leaves = tree.leaves*.value()
                    def preterminals = tree.preTerminalYield()*.value()
                    if (leaves.size() != preterminals.size()) {
                        println "PRETERMINAL/LEAF MISMATCH ${leaves.size()} ${preterminals.size()}"
                        println "${count} ${tree.depth()} ${tree.constituents().size()} ${leaves.size()}"
                        println leaves.join(' ')
                        println preterminals.join(' ')
                        println penn
                    }
                }

                println "${cleaned_file.path}\t${cleaned_file.parentFile.name}\t${count}"
            }


        }
    }

}

// edu.stanford.nlp:stanford-corenlp:1.3.4
