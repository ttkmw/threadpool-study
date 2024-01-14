import org.junit.jupiter.api.Test

import java.util.concurrent.CountDownLatch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPoolTest {

    @Test
    fun execute() {
        val threadPool = ThreadPool(3, 6, 1.toDuration(DurationUnit.NANOSECONDS))

        val numTasks = 10000
        val latch = CountDownLatch(numTasks)
        try {
            for (i in 0..<numTasks) {
                val myRunnable = object : Runnable {
                    override fun run() {
                        println("thread ${Thread.currentThread().name} is running task $i")
                        latch.countDown()
                    }

                    override fun toString(): String {
                        return "Task $i"
                    }
                }
                threadPool.execute(myRunnable)
            }
            latch.await()

        } finally {
            threadPool.shutdown()
        }
    }
}