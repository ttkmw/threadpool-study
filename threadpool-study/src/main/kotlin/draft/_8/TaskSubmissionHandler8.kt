package draft._8

import java.util.concurrent.Callable

interface TaskSubmissionHandler8 {

    companion object {
        fun ofDefault(): TaskSubmissionHandler8 {
            return DefaultTaskSubmissionHandler8()
        }
    }
    fun handleSubmission(task: Runnable, threadPool: ThreadPool8): TaskAction8
    fun handleSubmission(task: Callable<*>, threadPool: ThreadPool8): TaskAction8
    fun handleLateSubmission(task: Runnable, threadPool: ThreadPool8): TaskAction8
    fun handleLateSubmission(task: Callable<*>, threadPool: ThreadPool8): TaskAction8

}