package draft._8

import org.junit.jupiter.api.Test

import org.slf4j.LoggerFactory
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPool8Test {

    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool8Test::class.java)
    }

    @Test
    fun execute() {
        val threadPool8 = ThreadPool8(1, 1.toDuration(DurationUnit.NANOSECONDS))
        val numTasks = 10000
        try {
            for (i in 0 ..< numTasks) {
                threadPool8.execute {
                    logger.debug("${Thread.currentThread().name} is running task: $i")
                }
            }
        } finally {
            threadPool8.shutdown()
        }
    }
}