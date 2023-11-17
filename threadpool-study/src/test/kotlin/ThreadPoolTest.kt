import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch

class ThreadPoolTest {

    @Test
    fun execute() {
        val threadPool = ThreadPool(10)

        val numTasks = 10
        val latch = CountDownLatch(numTasks)
        for (i in 0 ..< numTasks) {
            threadPool.execute {
                println("thread ${Thread.currentThread().name} is running task: $i")
                latch.countDown()
            }
        }
        latch.await()
    }
}