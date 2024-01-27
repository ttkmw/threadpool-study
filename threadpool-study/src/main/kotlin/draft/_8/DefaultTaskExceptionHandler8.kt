package draft._8

import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

class DefaultTaskExceptionHandler8: TaskExceptionHandler8 {
    companion object {
        private val logger = LoggerFactory.getLogger(DefaultTaskSubmissionHandler8::class.java)
    }
    override fun handleException(task: Runnable, cause: Throwable, threadPool: ThreadPool8) {
        log(cause)
    }

    override fun handleException(task: Callable<*>, cause: Throwable, threadPool: ThreadPool8) {
        log(cause)
    }

    private fun log(cause: Throwable) {
        logger.warn("unexpected error occurred while running a task: ", cause)

    }
}