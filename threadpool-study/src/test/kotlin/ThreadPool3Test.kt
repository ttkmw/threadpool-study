import org.junit.jupiter.api.Test

import java.util.concurrent.CountDownLatch

class ThreadPool3Test {

    @Test
    fun execute() {
        val threadPool3 = ThreadPool3(100)
        val numTasks = 1000
        try {
            for (i in 0 ..< numTasks) {
                threadPool3.execute {
                    println("thread ${Thread.currentThread().name} is running task : $i")
                    Thread.sleep(20)
                }
            }
        } finally {
            threadPool3.shutdown()
        }
    }
}