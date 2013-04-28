
// for f in 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 ; do cat /corpora/LDC/LDC99T42/RAW/parsed/mrg/wsj/$f/*.mrg >> tmp/wsj_train.mrg ; done

wsj_data_dir = new File('/corpora/LDC/LDC99T42/RAW/parsed/mrg/wsj')

wsj_data_files = (2..21).collectMany { new File(wsj_data_dir, it as String).listFiles().grep { it.name =~ /.+\.mrg$/ } }

println wsj_data_files.size()
println wsj_data_files

split_wsj_data_dir = new File('tmp/split_wsj')
split_wsj_data_dir.mkdirs()

total_lines = 0

new MultiFileLineIterator(files:wsj_data_files.iterator()).each { if (it) total_lines += 1 }

println total_lines

ensemble_K = 20

split_N = (total_lines / ensemble_K).intValue()

println split_N
println split_N * ensemble_K

wsj_lines = new MultiFileLineIterator(files:wsj_data_files.iterator())

ensemble_K.times { split_i ->
    new File(split_wsj_data_dir, "wsj_${split_i}.mrg").withPrintWriter { w ->
        split_N.times {if (wsj_lines.hasNext()) w.println wsj_lines.next() }
    }
}

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

def readSexpList(Reader reader)
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
    while (cint >= 0) {
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
