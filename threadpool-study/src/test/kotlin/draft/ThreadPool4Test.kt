package draft

import draft.ThreadPool4
import org.junit.jupiter.api.Test

import java.util.concurrent.CountDownLatch

class ThreadPool4Test {

    @Test
    fun execute() {
        val threadPool4 = ThreadPool4(2)
        val numTasks = 1
        val latch = CountDownLatch(numTasks)
        try {
            for (i in 0 ..< numTasks) {
                threadPool4.execute {
                    println("ThreadPool ${Thread.currentThread().name} is running task : $i")
                    latch.countDown()
                }
            }
//            latch.await()
        } finally {
            threadPool4.shutdown()
        }
    }
}