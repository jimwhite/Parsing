package org.ifcx.parsing

import org.gradle.api.DefaultTask
import org.gradle.api.Task


class DefaultTaskWithEvaluate extends DefaultTask
{
    boolean isEvaluated = false

    def countDown = 0

    List<Closure> closuresToCall = []

    static List allWithEval = []

    DefaultTaskWithEvaluate() {
        allWithEval << this
    }

    void _afterEvaluate(Closure closure) {
        if (isEvaluated) {
            closure.call()
        } else {
            closuresToCall << closure
        }
    }

    void evaluated()
    {
        isEvaluated = true

        closuresToCall.each { it.call() }

        closuresToCall = null
    }

    void evaluateAfterAll(List<Task> tasks, Closure closure) {
        countDown += tasks.size()

        tasks.each { Task task ->
            if ((task instanceof DefaultTaskWithEvaluate || task instanceof SourceTaskWithEvaluate)
                    && !task.isEvaluated)
            {
                task._afterEvaluate {
                    if (--countDown == 0) {
                        closure.call()
                    }
                }
            } else {
                --countDown
            }
        }

        if (countDown == 0) closure.call()
    }
}
