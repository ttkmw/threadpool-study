package draft._8

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.slf4j.LoggerFactory

class ThreadPool8Test {

    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool8Test::class.java)
    }

    @Test
    fun execute() {
        val threadPool8 = ThreadPool8(10)
        val numTasks = 100
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