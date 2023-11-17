import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch

class ThreadPoolTest {

    @Test
    fun execute() {
        val threadPool = ThreadPool(100)

        val numTasks = 100
        try {
            for (i in 0 ..< numTasks) {
                threadPool.execute {
                    println("thread ${Thread.currentThread().name} is running task: $i")
                    Thread.sleep(200)
                }
            }
        } finally {
            threadPool.shutdown()
        }
    }
}