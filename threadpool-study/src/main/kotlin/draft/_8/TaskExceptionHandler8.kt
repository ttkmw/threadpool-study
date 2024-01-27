package draft._8

import java.util.concurrent.Callable

interface TaskExceptionHandler8 {

    companion object {
        fun ofDefault(): TaskExceptionHandler8 {
            return DefaultTaskExceptionHandler8()
        }
    }
    fun handleException(task: Runnable, cause: Throwable, threadPool: ThreadPool8)
    fun handleException(task: Callable<*>, cause: Throwable, threadPool: ThreadPool8)
}