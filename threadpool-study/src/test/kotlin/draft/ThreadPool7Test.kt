package draft

import org.junit.jupiter.api.Test

import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPool7Test {

    @Test
    fun execute() {
        val threadPool7 = ThreadPool7.builder(maxNumWorkers = 5)
            .minNumWorkers(1)
            .idleTimeout(1.toDuration(DurationUnit.NANOSECONDS))
            .build()
        val numTasks = 100
        try {
            for (i in 0 ..< numTasks) {
                threadPool7.execute {
                    println("${Thread.currentThread().name} is running task - $i")
                }
            }
        } finally {
            threadPool7.shutdown()
        }
    }
}