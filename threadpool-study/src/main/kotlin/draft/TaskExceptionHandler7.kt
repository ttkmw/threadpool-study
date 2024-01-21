package draft

import ThreadPool
import java.util.concurrent.Callable

interface TaskExceptionHandler7 {

    companion object {
        fun ofDefault(): TaskExceptionHandler7 {
            return DefaultTaskExceptionHandler7.INSTANCE
        }
    }
    fun handleException(task: Runnable, cause: Throwable, threadPool: ThreadPool7)

    fun handleException(task: Callable<*>, cause: Throwable, threadPool: ThreadPool7)
}