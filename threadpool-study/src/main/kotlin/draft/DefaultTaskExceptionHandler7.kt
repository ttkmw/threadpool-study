package draft

import ThreadPool
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

enum class DefaultTaskExceptionHandler7 : TaskExceptionHandler7 {
    INSTANCE;

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultTaskSubmissionHandler7::class.java)
    }

    override fun handleException(task: Runnable, cause: Throwable, threadPool: ThreadPool7) {
        log(task.toString(), cause)
    }

    override fun handleException(task: Callable<*>, cause: Throwable, threadPool: ThreadPool7) {
        log(task.toString(), cause)
    }

    private fun log(task: String, cause: Throwable) {
        logger.warn("exception occurred while running $task", cause)
    }
}