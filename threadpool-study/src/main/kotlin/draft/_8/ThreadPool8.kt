package draft._8

import org.slf4j.LoggerFactory
import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicBoolean

// 셧다운, 큐에있는거 다 돌때까지 기다리기
// addThreadIfNecessary
// idleTimeout
// builder
// submittedHandler
// exceptionHandler
// shutdownNow
// watchdog
// executorService
class ThreadPool8(private val numThreads: Int) : Executor{
    val threads: Array<Thread?> = arrayOfNulls(numThreads)
    val queue = LinkedTransferQueue<Runnable>()

    private val started = AtomicBoolean()
    private val shutdown = AtomicBoolean()

    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool8::class.java)
        private val SHUTDOWN_TASK = Runnable {  }
    }
    init {
        for (i in 0 ..< numThreads) {
            threads[i] = Thread {
                try {
                    while (true) {
                        try {
                            val task = queue.take()
                            if (task == SHUTDOWN_TASK) {
                                break
                            } else {
                                task.run()
                            }

                        } catch (t: Throwable) {
                            if (t !is InterruptedException) {
                                logger.debug("unexpected exception occurred", t)
                            }
                        }
                    }
                } finally {
                    logger.debug("${Thread.currentThread().name} is terminated")
                }
            }
        }
    }
    override fun execute(task: Runnable) {
        if (started.compareAndSet(false, true)) {
            for (thread in threads) {
                thread?.start()
            }
        }

        queue.add(task)
    }

    fun shutdown() {
        logger.debug("shutdown thread pool is started")
        if (shutdown.compareAndSet(false, true)) {
            for (i in 0 ..< numThreads) {
                queue.add(SHUTDOWN_TASK)
            }
        }

        for (thread in threads) {
            if (thread == null) {
                continue
            }
            do {
                try {
                    thread.join()
                } catch (_: InterruptedException) {}
            } while (thread.isAlive)
        }
    }

}