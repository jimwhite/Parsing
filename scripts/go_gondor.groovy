#!/usr/bin/env groovy

// James White Net ID jimwhite
// mailto:jimwhite@uw.edu
// Generate Condor command files and DAGman dag file.

if (args.length < 1) {
    System.err.println("Please supply name of script file (sans the '.groovy') for the job definitions.")
    return 1
}

gondor = new Gondor(args[0])

source_file = new File(args[0] + ".groovy")

evaluate(source_file)

// Generate the Condor DAG file
gondor.generate_dag(new File(args[0] + ".dag"))

///// Condor DAG Generator

class Gondor
{
// For input files that exist before any jobs are run, you should list them with these methods.
// Otherwise you'll get warning messages that the generator is assuming that files that aren't
// output by some job but are used as input must already exist.
// Note that these methods don't actually check whether the files do indeed exist.  Perhaps they should warn about that too.
// The add_data_files method will add all files recursively for any directories (and subdirectories).
// The add_data_dirs method will only add the file directly referred to (which may be a file or directory).
// Both methods accept path strings, File objects, or lists of them.

// Condor DAG Generator vars
    def job_counter = 0
    def output_files = [:]
    def all_jobs = []

    def environment = [:]

    // Standard output and error files will go here.  Log files have to go in /tmp because they can't be on NFS.
    def job_output_dir = new File('jobs')

    public Gondor(String prefix)
    {
        job_output_dir = new File(prefix + '_jobs')
        job_output_dir.mkdir()
    }

    def add_data_files(Object... data_files) { _add_data_files(data_files, true) }
    def add_data_dirs(Object... data_dirs) { _add_data_files(data_dirs, false) }

    def _add_data_files(data_files, Boolean recursive_add_files=true)
    {
        def data_job = [id:'', data:true]

        if ((data_files instanceof String) || (data_files instanceof File)) data_files = [data_files]

        data_files = data_files.flatten()

        data_files.each { data_file ->
            if (data_file instanceof String) data_file = new File((String) data_file)

            output_files[data_file] = data_job

            if (recursive_add_files && data_file.isDirectory()) {
                data_file.eachFileRecurse { output_files[it] = data_job }
            }
        }
    }

    def clone_environment(Object... vars)
    {
        // This is probably not a great idea, but it's here is you want it.
        // Note that this is just if the parameter list is empty as in clone_environment().
        // clone_environment([]) is a no-op (one parameter - no variable names).
        if (!vars) { vars = System.getenv().keySet() }

        vars.flatten().each { String var -> def value = System.getenv(var); if (value) environment[var] = value }
    }

    def prepend_classpath(Object... args)
    {
        def classpaths = normalize_file_argument(args).canonicalPath

        if (environment.CLASSPATH) classpaths << environment.CLASSPATH

        environment.CLASSPATH = classpaths.join(File.pathSeparator)
    }

    def condor_command(def script_file, List<String> vars)
    {
        if (script_file instanceof String) script_file = new File(script_file)
        // Note that we don't use absolute path here.  We just need the names to be unique locally.
        def script_name = script_file.path.replace(File.separatorChar, '_' as Character)
        def condor_cmd_file = new File(script_name + '.condor')

        def invars = []
        def outvars = []

        vars = vars.collect { String var ->
            switch (var) {
                case ~/.*\.in/ :
                    var = var.replaceFirst(/\.in$/, '')
                    invars.add(var)
                    break
                case ~/.*\.out/ :
                    var = var.replaceFirst(/\.out$/, '')
                    outvars.add(var)
            }
            var
        }

        def user = System.getProperty('user.name')

        if (!user) {
            // This shouldn't happen, but just in case...
            user = 'gondor' + (Math.random() * 100000 as Integer)
            // This may fail in a managed environment, but that isn't an issue here.
            System.setProperty('user.name', user)
        }

        // The command argument names are prefixed with an underscore (so they look like $(_foo))
        // because we don't want them to collide with the numerous Condor command variables.

        def condorTemplate = """\
####################
#
# James White (mailto:jimwhite@uw.edu)
#
####################

Universe   = vanilla
Environment = ${environment.collect { k, v -> k + '=' + v}.join(';') }
Executable  = $script_file
Arguments   = ${ vars.collect { it.endsWith('.flag') ? it - ~/\.flag$/ : '\$(_'+it+')' }.join(' ')}
Log         = /tmp/${user}_${script_name}.log
Output 		= \$(_MyJobOutput)
Error	    = \$(_MyJobError)
Request_Memory=6*1029
#Periodic_Remove = (RemoteWallClockTime - CumulativeSuspensionTime) > 1800)
Notification=Error
Queue
"""
        vars = vars.grep { !it.endsWith('.flag') }

        condor_cmd_file.write(condorTemplate)

        return { args ->
            if (args.is(null)) args = [:]

            def job_id = script_name + '_J' + (++job_counter)

            def outfile = new File(job_output_dir, "${job_id}.out")
            def errfile = new File(job_output_dir, "${job_id}.err")

            if (args.containsKey('outfile')) { outfile = args.remove('outfile') }
            if (args.containsKey('errfile')) { errfile = args.remove('errfile') }

            def job = [id: job_id, condor:condor_cmd_file, outfile:outfile, errfile:errfile, vars:vars, invars:invars, outvars:outvars, args:args, parents:[]];

            output_files[outfile] = job
            output_files[errfile] = job

            job.invars.each  {
                def arg = args[it]
                if (arg && !(arg instanceof File)) args[it] = normalize_file_argument(arg)
            }

            job.outvars.each {
                def arg = args[it]
                if (arg) {
                    if (!(arg instanceof File)) args[it] = arg = normalize_file_argument(arg)
                    if (arg instanceof File) {
                        output_files[arg] = job
                    } else {
                        arg.each { output_files[it] = job }
                    }
                }
            }

            all_jobs.add(job)

            job
        }
    }

    def condor_java_command(def executable, def jar_files, def main_class, List<String> vars)
    {
        def script_name = main_class
        def condor_cmd_file = new File(script_name + '.condor')

        def invars = []
        def outvars = []

        vars = vars.collect { String var ->
            switch (var) {
                case ~/.*\.in/ :
                    var = var.replaceFirst(/\.in$/, '')
                    invars.add(var)
                    break
                case ~/.*\.out/ :
                    var = var.replaceFirst(/\.out$/, '')
                    outvars.add(var)
            }
            var
        }

        def user = System.getProperty('user.name')

        if (!user) {
            // This shouldn't happen, but just in case...
            user = 'gondor' + (Math.random() * 100000 as Integer)
            // This may fail in a managed environment, but that isn't an issue here.
            System.setProperty('user.name', user)
        }

        // The command argument names are prefixed with an underscore (so they look like $(_foo))
        // because we don't want them to collide with the numerous Condor command variables.

        def condorTemplate = """\
####################
#
# James White (mailto:jimwhite@uw.edu)
#
####################

Universe   = java
Environment = ${environment.collect { k, v -> k + '=' + v}.join(';') }
JAR_Files   = ${jar_files.join(':')}
Executable  = ${executable}
Arguments   = ${main_class} -Xmx6g ${ vars.collect { it.endsWith('.flag') ? it - ~/\.flag$/ : '\$(_'+it+')' }.join(' ')}
Log         = /tmp/${user}_${script_name}.log
Output 		= \$(_MyJobOutput)
Error	    = \$(_MyJobError)
Request_Memory = 6*1024
#Periodic_Remove = (RemoteWallClockTime - CumulativeSuspensionTime) > 1800)
Queue
"""
//#Periodic_Remove=(ImageSize > 6300000) || (NumJobStarts =!= Undefined && NumJobStarts > 2)
//#Periodic_Remove=(NumJobStarts =!= Undefined && NumJobStarts > 2)

        vars = vars.grep { !it.endsWith('.flag') }

        condor_cmd_file.write(condorTemplate)

        return { args ->
            if (args.is(null)) args = [:]

            def job_id = script_name + '_J' + (++job_counter)

            def outfile = new File(job_output_dir, "${job_id}.out")
            def errfile = new File(job_output_dir, "${job_id}.err")

            if (args.containsKey('outfile')) { outfile = args.remove('outfile') }
            if (args.containsKey('errfile')) { errfile = args.remove('errfile') }

            def job = [id: job_id, condor:condor_cmd_file, outfile:outfile, errfile:errfile, vars:vars, invars:invars, outvars:outvars, args:args, parents:[]];

            output_files[outfile] = job
            output_files[errfile] = job

            job.invars.each  {
                def arg = args[it]
                if (arg && !(arg instanceof File)) args[it] = normalize_file_argument(arg)
            }

            job.outvars.each {
                def arg = args[it]
                if (arg) {
                    if (!(arg instanceof File)) args[it] = arg = normalize_file_argument(arg)
                    if (arg instanceof File) {
                        output_files[arg] = job
                    } else {
                        arg.each { output_files[it] = job }
                    }
                }
            }

            all_jobs.add(job)

            job
        }
    }

    def generate_dag(dag_file)
    {
        def warnings = 0

        dag_file.withPrintWriter { printer ->
            def dependencies = []
            all_jobs.each { job ->
                // println job
                printer.println "JOB ${job.id} ${job.condor}"

                // Variables that begin with a dash have a default value of themselves.
                job.vars.grep { it.startsWith('') }.each { if (!job.args.containsKey(it)) job.args[it] = it }

                if (!job.args.keySet().containsAll(job.vars)) {
                    printer.println "### WARNING: Missing arguments: ${job.vars - job.args.keySet()}"
                    ++warnings
                }

                if (!job.vars.containsAll(job.args.keySet())) {
                    printer.println "### WARNING: Extra arguments: ${job.args.keySet() - job.vars}"
                    ++warnings
                }

                printer.println "VARS ${job.id} " + ((job.vars.collect { var ->
                    if (job.invars.contains(var)) {
                        def input_files = job.args[var]
                        if (input_files instanceof File) input_files = [input_files]

                        input_files.each { File input_file ->
                            def parentJob = output_files[input_file]

                            if (!parentJob) {
                                printer.println ("### WARNING: Didn't find input file for generating parent dependency for '$var'!")
                                printer.println ("### Assuming file exists: ${input_file.canonicalPath}")
                                ++warnings
                            } else if (!parentJob.data) {
                                job.parents.add(parentJob)
                            }
                        }
                    }
                    '_' + var + '=\"' + argument_to_string(job.args[var]) + '\"'
                }) + ['_MyJobOutput=\"'+job.outfile.canonicalPath+'\"', '_MyJobError=\"'+job.errfile.canonicalPath+'\"']).join(' ')

                if (job.parents) dependencies.add("PARENT ${job.parents.id.unique().join(' ')} CHILD ${job.id}".toString())

                printer.println()
            }

            dependencies.each { printer.println it }
        }

        println "Generated ${all_jobs.size()} jobs for Condor DAG ${dag_file}"
        if (warnings) println "WARNING: ${warnings} warnings generated! See DAG file for details."
    }

    def normalize_file_argument(arg) {
        if (arg instanceof String) {
            new File(arg as String)
        } else {
            arg.collect { it instanceof File ? it : new File(it as String) }
        }
    }

    def argument_to_string(val)
    {
        if (val instanceof Collection) {
            (val.collect { it instanceof File ? it.canonicalPath : it }).join(' ')
        } else {
            val instanceof File ? val.canonicalPath : val
        }
    }
}
