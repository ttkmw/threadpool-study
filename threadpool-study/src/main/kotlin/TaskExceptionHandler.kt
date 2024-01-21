import java.util.concurrent.Callable

interface TaskExceptionHandler {

    companion object {
        fun ofDefault(): TaskExceptionHandler {
            return DefaultTaskExceptionHandler.INSTANCE
        }
    }

    fun handleTaskException(task: Runnable, cause: Throwable, threadPool: ThreadPool)
    fun handleTaskException(task: Callable<*>, cause: Throwable, threadPool: ThreadPool)
}