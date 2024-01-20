import draft.ThreadPool6
import org.junit.jupiter.api.Test

import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPool6Test {

    @Test
    fun execute() {
        val threadPool6 = ThreadPool6(3, 6, 1.toDuration(DurationUnit.NANOSECONDS))
        val numTasks = 100
//        val latch = CountDownLatch(numTasks)

        try {
            for (i in 0 ..< numTasks) {
                val task = object : Runnable {
                    override fun run() {
                        println("${Thread.currentThread().name} is running: task $i")
                    }
                    override fun toString(): String {
                        return "task $i"
                    }
                }
                threadPool6.execute(task)
            }
        } finally {
            threadPool6.shutdown()
        }
//        latch.await()
    }
}