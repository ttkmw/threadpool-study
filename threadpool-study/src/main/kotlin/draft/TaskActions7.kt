package draft

import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException

internal class TaskActions7 {
    companion object {
        val ACCEPT = object : TaskAction7 {
            override fun doAction(task: Runnable) {
            }

            override fun <T> doAction(task: Callable<T>) {

            }
        }

        val REJECT = object : TaskAction7 {
            override fun doAction(task: Runnable) {
                throw RejectedExecutionException()
            }

            override fun <T> doAction(task: Callable<T>) {
                throw RejectedExecutionException()
            }
        }

        val LOG = object : TaskAction7 {
            override fun doAction(task: Runnable) {
                log(task)
            }

            override fun <T> doAction(task: Callable<T>) {
                log(task)
            }

            private fun log(task: Any) {
                println("rejected a task $task")
            }
        }
    }
}