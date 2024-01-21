package draft

import java.util.concurrent.Callable

interface TaskSubmissionHandler7 {
    companion object {
        fun ofDefault(): TaskSubmissionHandler7 {
            return DefaultTaskSubmissionHandler7.INSTANCE

        }
    }
    fun handleSubmission(task: Runnable, numPendingTasks: Int): TaskAction7

    fun handleSubmission(task: Callable<*>, numPendingTasks: Int): TaskAction7

    fun handleLateSubmission(task: Runnable): TaskAction7

    fun handleLateSubmission(task: Callable<*>): TaskAction7
}