import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

enum class DefaultTaskExceptionHandler: TaskExceptionHandler {
    INSTANCE;

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultTaskSubmissionHandler::class.java)
    }
    override fun handleTaskException(task: Runnable, cause: Throwable, threadPool: ThreadPool) {
        log(cause)
    }

    override fun handleTaskException(task: Callable<*>, cause: Throwable, threadPool: ThreadPool) {
        log(cause)
    }

    private fun log(cause: Throwable) {
        logger.warn("unexpected exception occurred while running a task:", cause)
    }
}