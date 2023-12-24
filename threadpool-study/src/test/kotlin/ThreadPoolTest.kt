import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPoolTest {

    @Test
    fun execute() {
        val threadPool = ThreadPool(1, 1.toDuration(DurationUnit.NANOSECONDS))

        val numTasks = 2
        val latch = CountDownLatch(numTasks)
        try {
            for (i in 0 ..< numTasks) {
                threadPool.execute {
                    println("thread ${Thread.currentThread().name} is running task: $i")
                    try {
                        Thread.sleep(100)
                    } catch (_: InterruptedException) {
                        // I don't care
                    }
                    latch.countDown()
                }
            }
            latch.await()

        } finally {
            threadPool.shutdown()
        }
    }
}