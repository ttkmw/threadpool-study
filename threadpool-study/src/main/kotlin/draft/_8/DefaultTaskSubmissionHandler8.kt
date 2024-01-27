package draft._8

import java.util.concurrent.Callable

class DefaultTaskSubmissionHandler8: TaskSubmissionHandler8 {
    override fun handleSubmission(task: Runnable, threadPool: ThreadPool8): TaskAction8 {
        return TaskAction8.accept()
    }

    override fun handleSubmission(task: Callable<*>, threadPool: ThreadPool8): TaskAction8 {
        return TaskAction8.accept()
    }

    override fun handleLateSubmission(task: Runnable, threadPool: ThreadPool8): TaskAction8 {
        return TaskAction8.reject()
    }

    override fun handleLateSubmission(task: Callable<*>, threadPool: ThreadPool8): TaskAction8 {
        return TaskAction8.reject()
    }
}