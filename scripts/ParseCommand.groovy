import com.beust.jcommander.Parameter
import groovy.transform.Field
import org.ifcx.gondor.Command
import org.ifcx.gondor.WorkflowScript
import org.ifcx.gondor.api.InputDirectory
import org.ifcx.gondor.api.OutputFile

@groovy.transform.BaseScript WorkflowScript thisScript

@Parameter(names = '--model', description = 'Path to directory of model files.')
@InputDirectory @Field File model

@Parameter(names = '--input', description = 'Text input file.')
@OutputFile @Field File input

@Parameter(names = '--output', description = 'n-best parsed output file.')
@OutputFile @Field File output

def parseIt = command(path:'../bllip-parser/first-stage/PARSE/parseIt') {
    // Flag that is set for pre-tokenized input.
    arg 'tokenized', Command.OPTIONAL, { it ? '-K' : [] }

    // Maximum sentence length.
    arg 'sentenceLength', 400, { if (it) { assert it instanceof Number ; "-l$it" } else { [] } }

    // Number of best to parses output.
    arg 'bestCount', 50, { if (it) { assert it instanceof Number ; "-N$it" } else { [] } }

    // parseIt is fussy about the path to the model directory and it must have a trailing slash.
    // stringify returns a flat list of arguments as strings.
    infile 'model', { stringify(it).collect { it + '/' } }
    // That code could have been { (it as String) + '/' }
    // but if file path handling changes that might not work well.

    // Text input file
    infile 'input'
}

parseIt(tokenized:true, model:model, input:input) >> output
