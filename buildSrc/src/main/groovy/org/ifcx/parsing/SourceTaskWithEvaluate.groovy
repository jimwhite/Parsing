package org.ifcx.parsing

import org.gradle.api.Task
import org.gradle.api.tasks.SourceTask


class SourceTaskWithEvaluate extends SourceTask
{
    boolean isEvaluated = false

    def countDown = 0

    List<Closure> closuresToCall = []

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