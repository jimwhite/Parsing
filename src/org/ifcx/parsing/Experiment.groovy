package org.ifcx.parsing

import org.gradle.api.Task

class Experiment
{
    String name

    File experiment_dir

    def corpus

    def parser

    def parameters

    File parser_dir

    File model_dir

    Task setup_task

    Task train_task

    Task test_task


}
