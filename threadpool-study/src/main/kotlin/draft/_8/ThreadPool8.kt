package draft._8

import org.slf4j.LoggerFactory
import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

// addThreadIfNecessary
// idleTimeout
// builder
// submittedHandler
// exceptionHandler
// shutdownNow
// watchdog
// executorService
class ThreadPool8(private val maxNumThreads: Int) : Executor{
    val threads = HashSet<Thread>()
    val queue = LinkedTransferQueue<Runnable>()

    private val numThreads = AtomicInteger()
    private val numBusyThreads = AtomicInteger()

    private val threadsLock = ReentrantLock()
    private val shutdown = AtomicBoolean()

    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool8::class.java)
        private val SHUTDOWN_TASK = Runnable {  }
    }

    private fun newThread(): Thread {
        numThreads.incrementAndGet()
        numBusyThreads.incrementAndGet()
        val thread = Thread {
            var isBusy = true
            try {
                try {
                    while (true) {
                        try {
                            var task = queue.poll()
                            if (task == null) {
                                if (isBusy) {
                                    isBusy = false
                                    numBusyThreads.decrementAndGet()
                                }
                                task = queue.take()
                                isBusy = true
                                numBusyThreads.incrementAndGet()
                            } else {
                                if (!isBusy) {
                                    isBusy = true
                                    numBusyThreads.incrementAndGet()
                                }
                            }

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
                    threadsLock.lock()
                    try {
                        threads.remove(Thread.currentThread())
                    } finally {
                        threadsLock.unlock()
                    }
                    // q: lock 안에 있어야 하는 경우는 언제이지? idleTimeout 할 때 decrement 하는걸 락 안에 둬야하는 경우도 설명했었음.
                    numThreads.decrementAndGet()
                    if (isBusy) {
                        numBusyThreads.decrementAndGet()
                    }
                }
            } finally {
                logger.debug("${Thread.currentThread().name} is terminated")
            }
        }

        threads.add(thread)
        return thread
    }

    override fun execute(task: Runnable) {
        if (shutdown.get()) {
            throw RejectedExecutionException()
        }
        queue.add(task)
        addThreadIfNecessary()

        if (shutdown.get()) {
            queue.remove(task)
            throw RejectedExecutionException()
        }
    }

    private fun addThreadIfNecessary() {
        if (needsMoreThreads()) {
            threadsLock.lock()
            var newThread: Thread? = null
            try {
                if (needsMoreThreads() && !shutdown.get()) {
                    newThread = newThread()
                }
            } finally {
                threadsLock.unlock()
            }
            newThread?.start()
        }
    }

    private fun needsMoreThreads(): Boolean {
        val numThreads = numThreads.get()
        val numBusyThreads = numBusyThreads.get()
        return numBusyThreads >= numThreads && numThreads < maxNumThreads
    }

    fun shutdown() {
        logger.debug("shutdown thread pool is started")
        if (shutdown.compareAndSet(false, true)) {
            for (i in 0 ..< maxNumThreads) {
                queue.add(SHUTDOWN_TASK)
            }
        }

        val threads: Array<Thread>
        threadsLock.lock()
        try {
            threads = this.threads.toTypedArray()
        } finally {
            threadsLock.unlock()
        }

        for (thread in threads) {
            do {
                try {
                    thread.join()
                } catch (_: InterruptedException) {}
            } while (thread.isAlive)
        }
    }

}