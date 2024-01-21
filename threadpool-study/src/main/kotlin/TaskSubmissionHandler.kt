import java.util.concurrent.Callable

interface TaskSubmissionHandler {

    companion object {
        fun ofDefault(): TaskSubmissionHandler {
            return DefaultTaskSubmissionHandler.INSTANCE
        }
    }
    fun handleSubmission(task: Runnable, threadPool: ThreadPool): TaskAction
    fun handleSubmission(task: Callable<*>, threadPool: ThreadPool): TaskAction

    fun handleLateSubmission(task: Runnable, threadPool: ThreadPool): TaskAction
    fun handleLateSubmission(task: Callable<*>, threadPool: ThreadPool): TaskAction

}