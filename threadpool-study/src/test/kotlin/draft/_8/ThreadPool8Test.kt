package draft._8

import org.junit.jupiter.api.Test

import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ThreadPool8Test {

    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool8Test::class.java)
    }

    @Test
    fun shutdownNow() {
        val threadPool8 = ThreadPool8.builder(6)
            .minNumWorkers(3)
            .idleTimeout(1, TimeUnit.NANOSECONDS)
            .taskTimeout(1000, TimeUnit.MILLISECONDS)
            .watchdogIterationDuration(1, TimeUnit.SECONDS)
            .build()

        val numTasks = 50
        try {
            for (i in 0 ..< numTasks) {
                threadPool8.execute {
                    println("${Thread.currentThread().name} is running task - $i")
                    Thread.sleep(10000)
                }
            }
            Thread.sleep(2000)
            val result = CompletableFuture.runAsync { threadPool8.shutdown() }
            Thread.sleep(1000)
            println("isDone ${result.isDone}")
//            val unprocessed = threadPool8.shutdownNow()
//            println("size ${unprocessed.size}")
            println("waiting for shutdown")
            result.get()
            println("shutdown complete")
        } finally {
            threadPool8.shutdown()
        }
    }

    @Test
    fun execute() {
        val threadPool8 = ThreadPool8.builder(3)
            .minNumWorkers(2)
            .idleTimeout(1, TimeUnit.NANOSECONDS)
            .build()
        val numTasks = 1000
        try {
            for (i in 0..<numTasks) {
                threadPool8.execute {
                    logger.debug("${Thread.currentThread().name} is running task: $i")
                }
            }
        } finally {
            threadPool8.shutdown()
        }
    }

    @Test
    fun submissionHandler() {
        val oddTask = Runnable {}
        val threadPool8 = ThreadPool8.builder(2)
            .minNumWorkers(1)
            .idleTimeout(1, TimeUnit.NANOSECONDS)
            .submissionHandler(object: TaskSubmissionHandler8 {
                override fun handleSubmission(task: Runnable, threadPool: ThreadPool8): TaskAction8 {
                    if (task == oddTask) {
                        return TaskAction8.reject()
                    }
                    return TaskAction8.accept()
                }

                override fun handleSubmission(task: Callable<*>, threadPool: ThreadPool8): TaskAction8 {
                    if (task == oddTask) {
                        return TaskAction8.reject()
                    }
                    return TaskAction8.accept()
                }

                override fun handleLateSubmission(task: Runnable, threadPool: ThreadPool8): TaskAction8 {
                    return TaskAction8.reject()
                }

                override fun handleLateSubmission(task: Callable<*>, threadPool: ThreadPool8): TaskAction8 {
                    return TaskAction8.reject()
                }
            })
            .build()

        threadPool8.execute(oddTask)

    }
}