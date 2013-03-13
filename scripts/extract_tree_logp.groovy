def parse_lines = new File('parse1-sh.tmp').readLines() // .collect { it.trim() }.grep { it }

while (parse_lines) {
    def h = parse_lines.remove(0)

    if (h) {
        def (_, parse_count, sentence_id) = (h =~ /(\d+)[^.]+\.(.+)/)[0]
        parse_count = parse_count as Integer

        def first_p = 0

        parse_count.times { i ->
            def p = parse_lines.remove(0) as Double
            def x = parse_lines.remove(0)

            if (!i) first_p = p
        }

        parse_lines.remove(0)

        println "$sentence_id\t$first_p"
    }
}
