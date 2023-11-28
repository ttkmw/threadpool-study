import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch

class ThreadPool2Test {
    @Test
    fun execute() {
        val threadPool2 = ThreadPool2(10)
        val numTasks = 20

        val latch = CountDownLatch(numTasks)

        try {
            for (i in 0 ..< numTasks) {
                threadPool2.execute {
                    println("Thread ${Thread.currentThread().name} is running task - $i")
                    latch.countDown()
                }
            }

//            latch.await()
        } finally {
            threadPool2.shutdown()
        }

    }
}