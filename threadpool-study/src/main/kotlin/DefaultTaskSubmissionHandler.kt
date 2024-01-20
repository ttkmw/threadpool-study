import java.util.concurrent.Callable

enum class DefaultTaskSubmissionHandler : TaskSubmissionHandler {
    INSTANCE;
    override fun handleSubmission(task: Runnable, numPendingTasks: Int): TaskAction {
        return TaskActions.ACCEPT
    }

    override fun handleSubmission(task: Callable<*>, numPendingTasks: Int): TaskAction {
        return TaskActions.ACCEPT
    }

    override fun handleLateSubmission(task: Runnable): TaskAction {
        return TaskActions.REJECT
    }

    override fun handleLateSubmission(task: Callable<*>): TaskAction {
        return TaskActions.REJECT
    }
}