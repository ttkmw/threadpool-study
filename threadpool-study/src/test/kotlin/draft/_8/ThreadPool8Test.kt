package draft._8

import org.junit.jupiter.api.Test

import org.slf4j.LoggerFactory
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPool8Test {

    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool8Test::class.java)
    }

    @Test
    fun execute() {
        val threadPool8 = ThreadPool8(1, 3, 1.toDuration(DurationUnit.NANOSECONDS))
        val numTasks = 1000
        try {
            for (i in 0 ..< numTasks) {
                threadPool8.execute {
                    logger.debug("${Thread.currentThread().name} is running task: $i")
                }
                Thread.sleep(0,1)
            }
        } finally {
            threadPool8.shutdown()
        }
    }
}