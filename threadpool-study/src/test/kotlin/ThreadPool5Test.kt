import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch

class ThreadPool5Test {

    @Test
    fun execute() {
        val threadPool5 = ThreadPool5(10)
        val numTasks = 10
        try {
            for (i in 0 ..< numTasks) {
                threadPool5.execute {
                    println("Thread ${Thread.currentThread().name} is running task : $i")
//                    println("before sleep: ${threadPool5.numThreads.get()}, ${threadPool5.numActiveThreads.get()}, ${threadPool5.queue.size}")
                    Thread.sleep(200)
//                    println("after sleep: ${threadPool5.numThreads.get()}, ${threadPool5.numActiveThreads.get()}, ${threadPool5.queue.size}")
                }
//                Thread.sleep(20)
            }
        } finally {
            threadPool5.shutdown()
        }
    }
}