import java.util.concurrent.Callable
import kotlin.jvm.Throws

interface TaskAction {

    companion object {
        fun accept(): TaskAction {
            return TaskActions.ACCEPT
        }

        fun reject(): TaskAction {
            return TaskActions.REJECT
        }

        fun log(): TaskAction {
            return TaskActions.LOG
        }
    }
    fun doAction(task: Runnable)
    fun doAction(task: Callable<*>)
}