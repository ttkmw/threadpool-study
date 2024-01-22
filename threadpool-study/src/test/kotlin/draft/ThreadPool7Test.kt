package draft

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPool7Test {
    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool7::class.java)
    }

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

    @Test
    fun shutdownNow() {
        val threadPool7 = ThreadPool7.builder(maxNumWorkers = 6)
            .minNumWorkers(3)
            .build()
        val numTasks = 100
        try {
            for (i in 0 ..< numTasks) {
                threadPool7.execute(object: Runnable {
                    override fun run() {
                        println("processing task - $i")
                        Thread.sleep(10000)
                    }

                    override fun toString(): String {
                        return "task $i"
                    }

                })
            }


            Thread.sleep(2000)
            val shutdown = CompletableFuture.runAsync(threadPool7::shutdown)
            Thread.sleep(1000)
            logger.info("shutdown result {}", shutdown.isDone)
            val unprocessed = threadPool7.shutdownNow()
            logger.info("size {}", unprocessed.size)
            logger.info("{}", unprocessed)

            logger.info("waiting for shutdown")
            shutdown.get()
            logger.info("shutdown complete")

        } finally {
            threadPool7.shutdown()
        }
    }
}