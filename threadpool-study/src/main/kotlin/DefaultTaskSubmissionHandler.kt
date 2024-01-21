import java.util.concurrent.Callable

enum class DefaultTaskSubmissionHandler : TaskSubmissionHandler {
    INSTANCE;

    override fun handleSubmission(task: Runnable, threadPool: ThreadPool): TaskAction {
        return TaskAction.accept()
    }

    override fun handleSubmission(task: Callable<*>, threadPool: ThreadPool): TaskAction {
        return TaskAction.accept()
    }

    override fun handleLateSubmission(task: Runnable, threadPool: ThreadPool): TaskAction {
        return TaskAction.reject()
    }

    override fun handleLateSubmission(task: Callable<*>, threadPool: ThreadPool): TaskAction {
        return TaskAction.reject()
    }
}