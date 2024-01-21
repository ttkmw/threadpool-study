package draft

import java.util.concurrent.Callable

interface TaskAction7 {
    companion object {
        fun accept(): TaskAction7 {
            return TaskActions7.ACCEPT
        }

        fun reject(): TaskAction7 {
            return TaskActions7.REJECT
        }

        fun log(): TaskAction7 {
            return TaskActions7.LOG
        }
    }
    fun doAction(task: Runnable)
    fun <T> doAction(task: Callable<T>)
}