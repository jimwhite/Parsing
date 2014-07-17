import com.beust.jcommander.Parameter
import groovy.transform.Field
import org.ifcx.gondor.Command
import org.ifcx.gondor.WorkflowScript
import org.ifcx.gondor.api.InputDirectory
import org.ifcx.gondor.api.OutputFile

@groovy.transform.BaseScript WorkflowScript thisScript

//@Parameter(names = '--model', description = 'Path to directory of model files.')
//@InputDirectory @Field File model

@Parameter(names = '--input', description = 'Text input file.')
@OutputFile @Field File input

@Parameter(names = '--output', description = 'n-best parsed output file.')
@OutputFile @Field File output


def parseIt = command(path:'../bllip-parser/first-stage/PARSE/parseIt') {
    // Flag that is set for pre-tokenized input.
    arg 'tokenized', Command.OPTIONAL, { it ? '-K' : [] }

    // Maximum sentence length.
    arg 'sentenceLength', Command.OPTIONAL, { it ? "-l$it" : [] }

    // Number of best to parses output.
    arg 'bestCount', 50, { assert it instanceof Number ; [ "-N$it" ] }

    infile 'model', { stringify(it).collect { [ it + '/' ] } }

    infile 'input'
}

//arg(value:'-K')     // Input is tokenized
//arg(value:'-l400')  // Accept very long sentences.
//arg(value:"-N$number_of_parses")
//arg(value:model_dir.path + '/')
//arg(file:sent_file)

parseIt(tokenized:true, sentenceLength:222, model:new File('../bllip-parser/first-stage/DATA/EN/'), input:input) >> output
