
project("anydomain:WSJ") {
    // Wait until development is done to evaluate on WSJ Section 23.
    def final_configuration = false

  File WSJ_dir = project.projectDir
  def section_file_name = { String.format("%02d.mrg", it) }
  def section_files = { it.collect { section_num -> new File(WSJ_dir, section_file_name(section_num)) } }
  def check_output_files = { it.each { mrg_file -> if (!mrg_file.exists()) throw new FileNotFoundException("Missing corpus file: $mrg_file") } }
  
  task train_MRG {
     outputs.files(section_files(2..21)) 
     doFirst { check_output_files(outputs.files) }
  }

  task test_MRG {
     outputs.files(section_files([final_configuration ? 23 : 22]))
     doFirst { check_output_files(outputs.files) }
  }

  task tune_MRG {
     outputs.files(section_files([24]))
     doFirst { check_output_files(outputs.files) }
  }

  task list {
      [train_MRG, tune_MRG, test_MRG].each { splitTask ->
          dependsOn splitTask
          doLast { splitTask.outputs.files.each { println it } }
      }
  }
}

project("anydomain:Brown") {
    File Brown_dir = project.projectDir

    def check_output_files = { it.each { mrg_file -> if (!mrg_file.exists()) throw new FileNotFoundException("Missing corpus file: $mrg_file") } }

    task train_MRG {
        outputs.file new File(Brown_dir, 'train')
        doFirst { check_output_files(outputs.files) }
    }

    task tune_MRG {
        outputs.file new File(Brown_dir, 'tune')
        doFirst { check_output_files(outputs.files) }
    }

    task test_MRG {
        outputs.file new File(Brown_dir, 'test')
        doFirst { check_output_files(outputs.files) }
    }

    task list {
        [train_MRG, tune_MRG, test_MRG].each { splitTask ->
            dependsOn splitTask
            doLast { splitTask.outputs.files.each { println it } }
        }
    }
}

task list_WSJ {
    def corpus = "anydomain:WSJ"

    ["train_MRG", "test_MRG", "tune_MRG"].each { split ->
        dependsOn "$corpus:$split"
        doLast {
            println "WSJ $split files"
            project(corpus).tasks[split].outputs.files.each { println it }
        }
    }
}


task list << {
//    project.subprojects.each { name, corpus -> println name }
    project.subprojects.each { println it }
}

task list_WSJ_MRG {
    def corpus = ":corpora:anydomain:WSJ"

    ["train_MRG", "test_MRG", "tune_MRG"].each { split ->
        dependsOn "$corpus:$split"
        doLast {
            println "WSJ $split files"
            project(corpus).tasks[split].outputs.files.each { println it }
        }
    }
}

