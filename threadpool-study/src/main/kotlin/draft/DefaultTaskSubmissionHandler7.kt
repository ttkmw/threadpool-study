package draft

import java.util.concurrent.Callable

enum class DefaultTaskSubmissionHandler7: TaskSubmissionHandler7{
    INSTANCE;

    override fun handleSubmission(task: Runnable, numPendingTasks: Int): TaskAction7 {
        return TaskAction7.accept()
    }

    override fun handleSubmission(task: Callable<*>, numPendingTasks: Int): TaskAction7 {
        return TaskAction7.accept()
    }

    override fun handleLateSubmission(task: Runnable): TaskAction7 {
        return TaskAction7.reject()
    }

    override fun handleLateSubmission(task: Callable<*>): TaskAction7 {
        return TaskAction7.reject()
    }
}