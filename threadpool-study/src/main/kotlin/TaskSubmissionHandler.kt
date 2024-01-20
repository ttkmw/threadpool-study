import java.util.concurrent.Callable

interface TaskSubmissionHandler {

    companion object {
        fun ofDefault(): TaskSubmissionHandler {
            return DefaultTaskSubmissionHandler.INSTANCE
        }
    }
    fun handleSubmission(task: Runnable, numPendingTasks: Int): TaskAction
    fun handleSubmission(task: Callable<*>, numPendingTasks: Int): TaskAction

    fun handleLateSubmission(task: Runnable): TaskAction
    fun handleLateSubmission(task: Callable<*>): TaskAction

}