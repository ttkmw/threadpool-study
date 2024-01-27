package draft._8

import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException

interface TaskAction8 {

    companion object {
        fun accept(): TaskAction8 {
            return Accept
        }

        fun reject(): TaskAction8 {
            return Reject
        }

        fun log(): TaskAction8 {
            return Log
        }

        private val logger = LoggerFactory.getLogger(TaskAction8::class.java)

        private val Accept = object : TaskAction8{
            override fun doAction(task: Runnable) {}
            override fun doAction(task: Callable<*>) {}
        }
        private val Reject = object: TaskAction8 {
            override fun doAction(task: Runnable) {
                throw RejectedExecutionException()
            }

            override fun doAction(task: Callable<*>) {
                throw RejectedExecutionException()
            }
        }
        private val Log = object: TaskAction8 {
            override fun doAction(task: Runnable) {
                log(task)
            }

            override fun doAction(task: Callable<*>) {
                log(task)
            }

            private fun log(task: Any) {
                logger.warn("rejected a task {}", task)
            }

        }
    }






    fun doAction(task: Runnable)
    fun doAction(task: Callable<*>)
}