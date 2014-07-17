#!/usr/bin/env CLASSPATH=/Users/jim/Projects/Gondor/out/artifacts/gondor/gondor.jar /Users/jim/Projects/Groovy/groovy-2.4.0-SNAPSHOT/bin/groovy
//#!/usr/bin/env CLASSPATH=/Users/jim/Projects/Gondor/build/libs/Gondor-0.1.jar /Users/jim/Projects/Groovy/groovy-2.4.0-SNAPSHOT/bin/groovy

import com.beust.jcommander.Parameter
import groovy.transform.Field
import org.ifcx.gondor.Command
import org.ifcx.gondor.WorkflowScript
import org.ifcx.gondor.api.InputDirectory
import org.ifcx.gondor.api.InputFile
import org.ifcx.gondor.api.OutputFile

@groovy.transform.BaseScript WorkflowScript thisScript

@Parameter(names = '--format', required = true, description = 'Output format: gold (-e) or sentence (-c).')
@Field String mode

@Parameter(names = '--input', required = true, description = 'PTB input file.')
@InputFile @Field File input

@Parameter(names = '--output', required = true, description = 'Converted output file.')
@OutputFile @Field File output

def bllip_parser_dir = new File('../bllip-parser')
def ptb_executable = new File(bllip_parser_dir, 'second-stage/programs/prepare-data/ptb')

def ptb_convert = command(path:ptb_executable.path) {
    // Flag that is set for pre-tokenized input.
    arg 'mode', Command.REQUIRED, [tree:'-e', text:'-c']

    // PTB input file
    infile 'input'
}

ptb_convert(mode:mode, input:input) >> output
