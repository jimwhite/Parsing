package org.ifcx.parsing

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.SourceTask

class ConvertManyPTB extends SourceTask {
    @Input
    String mode

//    @InputDirectory
//    File bllip_parser_dir

    @Input
    File output_dir

    ConvertManyPTB() {
        project.afterEvaluate {
//            def ptb_executable = new File(bllip_parser_dir, 'second-stage/programs/prepare-data/ptb')
            def ptb_executable = new File(project.rootDir, 'bllip-parser/second-stage/programs/prepare-data/ptb')

            doFirst {
                ant.mkdir(dir:output_dir)
            }

            def file_ext = ''

            switch (mode) {
                case '-e' : file_ext = '.gold' ; break
                case '-c' : file_ext = '.sent' ; break
            }

            source.each { ptb_file ->
                def output_file = new File(output_dir, ptb_file.name + file_ext)
                def error_file = new File(output_dir, output_file.name + '.err')

                outputs.files(output_file)

                doLast {
                    ant.exec(executable:ptb_executable, dir:output_dir, failonerror:true
                            , output:output_file, error:error_file) {
                        arg(value:mode)
                        arg(file:ptb_file)
                    }
                }
            }
        }
    }

//    class MyFileCopyActionImpl extends FileCopyActionImpl {
//        boolean didSomething = false
//
//        MyFileCopyActionImpl(FileResolver resolver, CopySpecVisitor visitor) {
//            super(resolver, visitor)
//        }
//
//        void execute() {
//            def ptb_executable = new File(bllip_parser_dir, 'second-stage/programs/prepare-data/ptb')
//
//            for (ReadableCopySpec spec : getRootSpec().allSpecs) {
//                visitor.visitSpec(spec);
//                spec.getSource().visit(visitor);
//            }
//
//            source.each { ptb_file ->
//                def output_file = new File(output_dir, ptb_file.name + '.sent')
//                def error_file = new File(output_dir, ptb_file.name + '.err')
//                ant.exec(executable:ptb_executable, dir:output_dir, failonerror:true
//                        , output:output_file, error:error_file) {
//                    arg(value:mode)
//                    arg(file:ptb_file)
//                }
//            }
//        }
//
//        boolean getDidWork() { didSomething }
//    }
//
//    /////
//    // Boilerplate from org.gradle.api.tasks.Copy
//    ////
//
//    private FileCopyActionImpl copyAction;
//
//    ConvertManyPTB() {
//        FileResolver fileResolver = getServices().get(FileResolver.class);
//        copyAction = new MyFileCopyActionImpl(fileResolver, new FileCopySpecVisitor());
//    }
//
//    @Override
//    protected FileCopyActionImpl getCopyAction() { copyAction }
//
//    /**
//     * Returns the directory to copy files into.
//     *
//     * @return The destination dir.
//     */
//    @OutputDirectory
//    public File getDestinationDir() {
//        return getCopyAction().getDestinationDir();
//    }
//
//    /**
//     * Sets the directory to copy files into. This is the same as calling {@link #into(Object)} on this task.
//     *
//     * @param destinationDir The destination directory. Must not be null.
//     */
//    public void setDestinationDir(File destinationDir) {
//        into(destinationDir);
//    }

}
