package draft._8

import org.slf4j.LoggerFactory
import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration


// builder
// submittedHandler
// exceptionHandler
// shutdownNow
// watchdog
// executorService
class ThreadPool8(private val maxNumThreads: Int, idleTimeout: Duration) : Executor{
    val threads = HashSet<Thread>()
    val queue = LinkedTransferQueue<Runnable>()

    private val numThreads = AtomicInteger()
    private val numBusyThreads = AtomicInteger()

    private val threadsLock = ReentrantLock()
    private val shutdown = AtomicBoolean()

    private val idleTimeoutNanos = idleTimeout.inWholeNanoseconds

    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool8::class.java)
        private val SHUTDOWN_TASK = Runnable {  }
    }

    private fun newThread(): Thread {
        numThreads.incrementAndGet()
        numBusyThreads.incrementAndGet()

        val thread = Thread {
            val threadName = Thread.currentThread().name
            logger.debug("Started a new thread {}", threadName)
            var isBusy = true
            var lastTimeoutNanos = System.nanoTime()
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
                                val waitTimeNanos = idleTimeoutNanos - (System.nanoTime() - lastTimeoutNanos)
                                if (waitTimeNanos < 0) {
                                    logger.debug("{} stops doing work because {} haven't work too long time", threadName, threadName)
                                    break
                                }
                                task = queue.poll(waitTimeNanos, TimeUnit.NANOSECONDS)
                                if (task == null) {
                                    logger.debug("{} stops doing work because there is no work for some time", threadName)
                                    break
                                }
                                isBusy = true
                                numBusyThreads.incrementAndGet()
                            } else {
                                if (!isBusy) {
                                    isBusy = true
                                    numBusyThreads.incrementAndGet()
                                }
                            }

                            if (task == SHUTDOWN_TASK) {
                                logger.debug("{} received a command that 'do not work'. {} stops doing work", threadName, threadName)
                                break
                            } else {
                                task.run()
                                lastTimeoutNanos = System.nanoTime()
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
                        // q: lock 안에 있어야 하는 경우는 언제이지? idleTimeout 할 때 decrement 하는걸 락 안에 둬야하는 경우도 설명했었음.
                        numThreads.decrementAndGet()
                        if (isBusy) {
                            numBusyThreads.decrementAndGet()
                        }
                        if (threads.isEmpty() && queue.isNotEmpty()) {
                            if (queue.any { it != SHUTDOWN_TASK }) {
                                addThreadIfNecessary()
                            }
                        }
                    } finally {
                        threadsLock.unlock()
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

        // q: 얘 왜붙였었지
        while (true) {
            val threads: Array<Thread>
            threadsLock.lock()
            try {
                threads = this.threads.toTypedArray()
            } finally {
                threadsLock.unlock()
            }

            if (threads.isEmpty()) {
                break
            }

            for (thread in threads) {
                do {
                    try {
                        thread.join()
                    } catch (_: InterruptedException) {}
                    // q: 얠 붙였던건 interrupt 될수도 있으니까 맞나
                } while (thread.isAlive)
            }
        }
    }

}