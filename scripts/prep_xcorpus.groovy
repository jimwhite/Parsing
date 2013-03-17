// James White mailto:jimwhite@uw.edu

/////////////////////////////
// Environmental Dependencies
/////////////////////////////

// If there are environment variables you want to copy from the current process, use clone_environment:
// gondor.clone_environment('PATH', 'ANT_HOME', 'JAVA_HOME')
// If you want to copy *all* of the the current environment variables, omit the variable names (not recommended):
// gondor.clone_environment()

gondor.environment = [PATH:"/usr/kerberos/bin:/usr/local/bin:/bin:/usr/bin:/opt/git/bin:/opt/scripts:/condor/bin"
        , LC_COLLATE:'C'  // first-stage/PARSE/parseIt needs this to deal with punctuation correctly!
                            // Not really.  Need to use -K (pretokenized) flag.
]

/////////////
// Data Files
/////////////

workspace_dir = new File('/home2/jimwhite/workspace/parsers')

bllip_dir = new File(workspace_dir, 'bllip-parser')

xcorpus_dir = new File(workspace_dir, 'xcorpus')
xcorpus_dir.mkdirs()

brown_mrg_dir = new File('/corpora/LDC/LDC99T42/RAW/parsed/mrg/brown')

/////////////////////////////
// Condor Command Definitions
/////////////////////////////

convert_ptb = gondor.condor_command(new File(bllip_dir, 'second-stage/programs/prepare-data/ptb'), ['mode', 'from.in'])

//////////////////////
// Job DAG Definitions
//////////////////////

original_ptb_files = brown_mrg_dir.listFiles().grep { it.isDirectory() }.collectMany { it.listFiles().grep { it.name.endsWith ".mrg" } }

new File(xcorpus_dir, 'filelist.txt').withPrintWriter { filelist_printer ->
    original_ptb_files.each { File original_ptb ->
        def section_name = original_ptb.parentFile.name
        def file_name = original_ptb.name

        filelist_printer.println section_name + File.separator + file_name

        def section_dir = new File(xcorpus_dir, section_name)
        section_dir.mkdirs()

        def charniak_input = new File(section_dir, file_name + ".sent")
        def evalb_gold = new File(section_dir, file_name + ".eval")

        if (!charniak_input.exists()) convert_ptb(mode:'-c', from:original_ptb, outfile:charniak_input)
        if (!evalb_gold.exists()) convert_ptb(mode:'-e', from:original_ptb, outfile:evalb_gold)
    }
}
