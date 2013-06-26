package org.ifcx.parsing

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

class ConvertManyPTB extends SourceTask {
    @Input
    String mode
    @OutputFile
    File outputFile

    @TaskAction
    void convert() {
        new FileOutputStream(outputFile).withStream { os ->
            Exec ptb = project.task (name + "-exec", type:Exec)
            ptb.executable 'bllip-parser/second-stage/programs/prepare-data/ptb'
            ptb.setArgs([mode, source.singleFile])
            ptb.setStandardOutput os
            ptb.execute()
            println "ran ptb $ptb.name"
        }
    }

}
