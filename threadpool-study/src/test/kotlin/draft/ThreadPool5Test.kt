package draft

import draft.ThreadPool5
import org.junit.jupiter.api.Test

import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPool5Test {

    @Test
    fun execute() {
        val threadPool5 = ThreadPool5(10, 1.toDuration(DurationUnit.NANOSECONDS))
        val numTasks = 10000
        try {
            for (i in 0 ..< numTasks) {
                val task = object : Runnable {
                    override fun run() {
                        println("Thread ${Thread.currentThread().name} is running task : $i")
                    }

                    override fun toString(): String {
                        return "Task $i"
                    }
                }
                threadPool5.execute(task)
            }
        } finally {
            threadPool5.shutdown()
        }
    }
}