#!/usr/bin/env groovy

keepers = args[0] as Integer
period = args[1] as Integer

def best = new File(args[2])

int i = keepers
int j = period

best.eachLine { line ->
    if (i-- > 0) {
        println(line.replaceFirst(/^\(S1/, "("))
    }

    if (--j < 1) {
        i = keepers
        j = period
    }
}
