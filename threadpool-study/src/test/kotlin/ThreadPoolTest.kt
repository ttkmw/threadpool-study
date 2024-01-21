import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

import java.util.concurrent.CountDownLatch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPoolTest {
    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPoolTest::class.java)
    }

    @Test
    fun customTaskSubmissionHandler() {
        val taskToReject = Runnable {}
        val threadPool = ThreadPool.builder(1)
            .submissionHandler(object: TaskSubmissionHandler {
                override fun handleSubmission(task: Runnable, threadPool: ThreadPool): TaskAction {
                    return if (task == taskToReject) {
                        TaskActions.REJECT
                    } else {
                        TaskActions.ACCEPT
                    }
                }

                override fun handleSubmission(task: Callable<*>, threadPool: ThreadPool): TaskAction {
                    throw UnsupportedOperationException()
                }

                override fun handleLateSubmission(task: Runnable, threadPool: ThreadPool): TaskAction {
                    return TaskActions.REJECT
                }

                override fun handleLateSubmission(task: Callable<*>, threadPool: ThreadPool): TaskAction {
                    return TaskActions.REJECT
                }
            }).build()

        val latch = CountDownLatch(1)
        threadPool.execute { latch.countDown() }
        latch.await()

        threadPool.execute(taskToReject)


    }

    @Test
    fun execute() {
        val threadPool = ThreadPool.builder(6)
            .minNumWorkers(3)
            .build()

        val numTasks = 100
        val latch = CountDownLatch(numTasks)
        try {
            for (i in 0..<numTasks) {
                val myRunnable = object : Runnable {
                    override fun run() {
                        try {
                            println("${Thread.currentThread().name} is running - $this")
                            Thread.sleep(10000)
                        } catch (t: InterruptedException) {
                            Thread.currentThread().interrupt()
                        } finally {
                            latch.countDown()
                        }
                    }

                    override fun toString(): String {
                        return "Task $i"
                    }
                }
                threadPool.execute(myRunnable)
            }

            Thread.sleep(2000)
            val shutdownFuture = CompletableFuture.runAsync(threadPool::shutdown)
            Thread.sleep(1000)
            logger.info("shutdownFuture.isDone(): {}", shutdownFuture.isDone)

            val remainingTasks = threadPool.shutdownNow()
            logger.info("Unexecuted tasks {}", remainingTasks.size)
            logger.info("{}", remainingTasks)


            logger.info("Waiting for shutdown() to complete")
            shutdownFuture.get()
            logger.info("Shutdown complete")

        } finally {
            threadPool.shutdown()
        }
    }
}