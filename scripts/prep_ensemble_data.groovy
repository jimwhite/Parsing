
// Use `ls` to make sure file names are in sorted order which we need for repeatability.
// for f in 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 ; do cat `ls /corpora/LDC/LDC99T42/RAW/parsed/mrg/wsj/$f/*.mrg` >> tmp/wsj_train.mrg ; done

wsj_data = new File(args[0])

split_wsj_data_dir = new File(args[1])
split_wsj_data_dir.mkdirs()
split_wsj_prefix = wsj_data.name - ~/\.mrg$/

total_sentences = 0

wsj_data.withReader { reader ->
    def sexp = read_one_sexp(reader)
    while (sexp) {
        total_sentences += 1
        sexp = read_one_sexp(reader)
    }
}

println total_sentences

ensemble_K = 20

split_N = (total_sentences / ensemble_K).intValue()

println split_N
println split_N * ensemble_K

wsj_data.withReader { wsj ->
    ensemble_K.times { split_i ->
        new File(split_wsj_data_dir, String.format('wsj_train%02d.mrg', split_i)).withPrintWriter { w ->
            split_N.times { w.println sexp_to_string(read_one_sexp(wsj)) }
        }
    }
}

def read_one_sexp(Reader reader)
{
    // This grammar has single quotes in token names.
//    final tokenDelimiters = "\"''()\t\r\n "
//    final tokenDelimiters = "\"()\t\r\n "
    // No quoted strings at all for these s-exprs.
    final tokenDelimiters = "()\t\r\n "

    def stack = []
    def sexps = []

    def cint = reader.read()

    loop:
    while (cint >= 0 && (stack.size() > 1 || sexps.size() < 1)) {
        Character c = cint as Character
        switch (c) {

            case ')' :
                if (stack.size() < 1) break loop
                def t = stack.pop()
                t << sexps
                sexps = t
                cint = reader.read()
                break

            case '(':

                stack.push(sexps)
                sexps = []
                cint = reader.read()
                break

            default:
                if (c.isWhitespace()) {
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

    return sexps
}

def sexp_to_string(sexp)
{
    (sexp instanceof List) ? "(${sexp.collect { sexp_to_string(it) }.join(' ')})" : sexp.toString()
}

// EOF

//LABELS_TO_DELETE = ["TOP", "S1", "-NONE-", ",", ":", "``", "''", "."] as Set
LABELS_TO_DELETE = [] as Set

class MultiFileLineIterator implements Iterator<String>
{
    Iterator<File> files
    Iterator<String> lines = null

    @Override
    boolean hasNext() {
        while (lines == null || !lines.hasNext()) {
            if (files.hasNext()) {
                lines = files.next().newReader().iterator()
            } else {
                lines = null
                return false
            }
        }

        return lines.hasNext()
    }

    @Override
    String next() {
        lines.next().trim()
    }

    @Override
    void remove() {
        throw new UnsupportedOperationException()
    }
}

def canonical_tree_string(Reader reader)
{
    def sexpList = read_one_sexp(reader)

    if (sexpList.size() == 1) {
        clean_tree(sexpList.head())
    } else {
        System.err.println("Didn't get exactly one tree from read_one_sexp.\n" + sexpList)
        tree
    }
}

def clean_tree(tree)
{
    if (tree instanceof List) {
        if (tree.head() in LABELS_TO_DELETE) {
            if (tree.size() == 2) {
                if (tree[1] instanceof List) {
                    tree = clean_tree(tree[1])
                } else {
                    tree = []
                }
            } else {
//                System.err.println("Tried to delete label ${tree.head()} but length is ${tree.size()} for:\n" + tree)
                tree = tree.tail().collect { clean_tree(it) }.grep { it }

                if (tree.size() == 1) tree = tree.head()
//                println tree
            }
        } else {
            tree = [clean_label(tree.head())] + tree.tail().collect { clean_tree(it) }.grep { it }

            if (tree.size() == 1) tree = []
        }
    }

    tree
}

def clean_label(String label)
{
    label = label.replaceFirst(/[-=].*$/, '')

    // EQ_LABEL ADVP PRT
    if (label == 'PRT') label = 'ADVP'

    label
}

