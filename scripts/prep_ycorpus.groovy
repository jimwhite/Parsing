#!/usr/bin/env groovy

brown_corpus_file = new File(args[0])

ycorpus_dir = new File(args[1])

train_file = new File(ycorpus_dir, 'brown-train.mrg')
test_file  = new File(ycorpus_dir, 'brown-test.mrg')
dev_file = new File(ycorpus_dir, 'brown-dev.mrg')

//file_list = new File(xcorpus_dir, 'filelist.txt').readLines()
//println file_list

total_sentences = 0

brown_corpus_file.withReader { reader ->
    train_file.withPrintWriter { train_writer ->
    test_file.withPrintWriter { test_writer ->
    dev_file.withPrintWriter { dev_writer ->
        def sexp = read_one_sexp(reader)
        while (sexp) {
            switch (total_sentences % 10) {
                case 8:
                    test_writer.println sexp_to_string(sexp)
                    test_writer.println()
                    break
                case 9:
                    dev_writer.println sexp_to_string(sexp)
                    dev_writer.println()
                    break
                default:
                    train_writer.println sexp_to_string(sexp)
                    train_writer.println()
                    break
            }

//            if (total_sentences % 100 == 0) println "\nSentence $total_sentences"

            total_sentences += 1

            sexp = read_one_sexp(reader)

//            while (!sexp && reader.ready()) {
//                sexp = read_one_sexp(reader)
//                if (sexp) {
//                    println "Got empty sexp but found one after that"
//                    println "====="
//                    println sexp_to_string(sexp)
//                    println "====="
//                }
//            }
        }
    }
    }
    }
}

println()
println total_sentences

def read_one_sexp(Reader reader)
{
    // This grammar has single quotes in token names.
//    final tokenDelimiters = "\"''()\t\r\n "
//    final tokenDelimiters = "\"()\t\r\n "
    // No quoted strings at all for these s-exprs.
    final tokenDelimiters = "()\t\r\n "

    List stack = []
    List sexps = []

    def cint = reader.read()

    loop:
    while (cint >= 0) {
        Character c = cint as Character
//        print c
        switch (c) {

            case ')' :
                // End of sexp with without beginning.
                // Print a warning?
                if (stack.size() < 1) break loop

                def t = stack.pop()
                t << sexps
                sexps = t

                // We read only one complete sexp.
                if (stack.size() < 1) break loop

                cint = reader.read()
                break

            case '(':

                stack.push(sexps)
                sexps = []
                cint = reader.read()
                break

            default:
                if (c.isWhitespace() || c == 0 || c == 26 /* ASCII EOF */ ) {
                    cint = reader.read()
                } else {
                    def token = new StringBuilder()
                    token.append(c)
                    while ((cint = reader.read()) >= 0) {
                        if (tokenDelimiters.indexOf(cint) >= 0) break
                        token.append(cint as Character)
                    }
                    sexps << token.toString()
                }
        }
    }

    return sexps ? sexps[0] : null
}

def sexp_to_string(sexp)
{
    (sexp instanceof List) ? "(${sexp.collect { sexp_to_string(it) }.join(' ')})" : sexp.toString()
}
