#!/bin/env groovy

original_brown_corpus = new File('/corpora/LDC/LDC99T42/RAW/parsed/mrg/brown')

brown_corpus = new File('corpora/brown/mrg')

original_brown_corpus.eachFileRecurse { File original_file ->
    if (original_file.isFile()) {
        def cleaned_file = new File(brown_corpus, original_file.absolutePath - original_brown_corpus.absolutePath)

        println cleaned_file.path

        if (!cleaned_file.parentFile.isDirectory()) cleaned_file.parentFile.mkdirs()

        def lines = original_file.readLines()

        while (lines && lines[0].startsWith("*")) lines.remove(0)

        cleaned_file.write(lines.join('\n'))
    }

}
