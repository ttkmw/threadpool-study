import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPoolTest {

    @Test
    fun execute() {
        val threadPool = ThreadPool(100, 1.toDuration(DurationUnit.SECONDS))

        val numTasks = 100
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

            while (true) {
                threadPool.threadLock.lock()
                var threads: Array<Thread>
                try {
                    threads = threadPool.threads.toTypedArray()
                } finally {
                    threadPool.threadLock.unlock()
                }
                for (thread in threads) {
                    thread.interrupt()
                    Thread.sleep(100)
                }
            }

        } finally {
            threadPool.shutdown()
        }
    }
}