import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

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
            .idleTimeout(1.toDuration(DurationUnit.NANOSECONDS))
            .build()

        val numTasks = 1000
        val latch = CountDownLatch(numTasks)
        try {
            for (i in 0..<numTasks) {
                val myRunnable = object : Runnable {
                    override fun run() {
                        logger.info("thread {} is running task {}", Thread.currentThread().name, i)
                        latch.countDown()
                    }

                    override fun toString(): String {
                        return "Task $i"
                    }
                }
                threadPool.execute(myRunnable)
            }
            latch.await()

        } finally {
            threadPool.shutdown()
        }
    }
}