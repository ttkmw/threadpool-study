import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException

class TaskActions private constructor() {
    companion object {
        val ACCEPT: TaskAction = object: TaskAction {
            override fun doAction(task: Runnable) {
            }

            override fun doAction(task: Callable<*>) {
            }
        }

        val REJECT: TaskAction = object: TaskAction {
            override fun doAction(task: Runnable) {
                throw RejectedExecutionException()
            }

            override fun doAction(task: Callable<*>) {
                throw RejectedExecutionException()
            }
        }

        val LOG: TaskAction = object: TaskAction {
            override fun doAction(task: Runnable) {
                log(task)
            }

            override fun doAction(task: Callable<*>) {
                log(task)
            }

            private fun log(task: Any) {
                println("Rejected task : $task")
            }
        }
    }
}