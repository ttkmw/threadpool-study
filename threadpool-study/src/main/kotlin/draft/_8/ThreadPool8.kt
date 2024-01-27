package draft._8

import org.slf4j.LoggerFactory
import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

// builder
// submittedHandler
// exceptionHandler
// shutdownNow
// watchdog
// executorService
class ThreadPool8(private val minNumWorkers: Int, private val maxNumWorkers: Int, private val idleTimeoutNanos: Long) : Executor{
    val workers = HashSet<Worker>()
    val queue = LinkedTransferQueue<Runnable>()

    private val numWorkers = AtomicInteger()
    private val numBusyWorkers = AtomicInteger()

    private val workersLock = ReentrantLock()
    private val shutdown = AtomicBoolean()

    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool8::class.java)
        private val SHUTDOWN_TASK = Runnable {  }

        fun of(maxNumWorkers: Int): ThreadPool8 {
            return builder(maxNumWorkers).build()
        }

        fun of(minNumWorkers: Int, maxNumWorkers: Int): ThreadPool8 {
            return builder(maxNumWorkers)
                .minNumWorkers(minNumWorkers)
                .build()
        }

        fun builder(maxNumWorkers: Int): ThreadPoolBuilder8 {
            return ThreadPoolBuilder8(maxNumWorkers)
        }
    }

    private fun newWorker(expirationMode: ExpirationMode): Worker {
        numWorkers.incrementAndGet()
        numBusyWorkers.incrementAndGet()

        val worker = Worker(expirationMode)

        workers.add(worker)
        return worker
    }

    override fun execute(task: Runnable) {
        if (shutdown.get()) {
            throw RejectedExecutionException()
        }
        queue.add(task)
        addWorkersIfNecessary()

        if (shutdown.get()) {
            queue.remove(task)
            throw RejectedExecutionException()
        }
    }

    private fun addWorkersIfNecessary() {
        if (needsMoreWorkers() != null) {
            workersLock.lock()
            var newWorkers: MutableList<Worker>? = null
            try {
                while (!shutdown.get()) {
                    val workerType = needsMoreWorkers() ?: break
                    if (newWorkers == null) {
                        newWorkers = ArrayList()
                    }
                    newWorkers.add(newWorker(workerType))
                }
            } finally {
                workersLock.unlock()
            }
            newWorkers?.forEach { it.start() }
        }
    }

    private fun needsMoreWorkers(): ExpirationMode? {
        val numWorkers = numWorkers.get()
        val numBusyWorkers = numBusyWorkers.get()
        if (numWorkers < minNumWorkers) {
            return ExpirationMode.NEVER
        }

        if (numBusyWorkers >= numWorkers && numWorkers < maxNumWorkers) {
            return if (idleTimeoutNanos > 0) ExpirationMode.ON_IDLE_TIMOUT else ExpirationMode.NEVER
        }

        return null
    }

    fun shutdown() {
        logger.debug("shutdown thread pool is started")
        if (shutdown.compareAndSet(false, true)) {
            for (i in 0 ..< maxNumWorkers) {
                queue.add(SHUTDOWN_TASK)
            }
        }

        // q: 얘 왜붙였었지
        while (true) {
            val workers: Array<Worker>
            workersLock.lock()
            try {
                workers = this.workers.toTypedArray()
            } finally {
                workersLock.unlock()
            }

            if (workers.isEmpty()) {
                break
            }

            for (w in workers) {
                w.join()
            }
        }
    }

    inner class Worker(private val expirationMode: ExpirationMode) {
        private val thread = Thread(this::work)

        fun start() {
            thread.start()
        }

        private fun work() {
            val threadName = Thread.currentThread().name
            logger.debug("Started a new thread {} ({})", threadName, expirationMode)
            var isBusy = true
            var lastTimeoutNanos = System.nanoTime()
            try {
                while (true) {
                    try {
                        var task = queue.poll()
                        if (task == null) {
                            if (isBusy) {
                                isBusy = false
                                numBusyWorkers.decrementAndGet()
                            }
                            val waitTimeNanos = idleTimeoutNanos - (System.nanoTime() - lastTimeoutNanos)
                            when (expirationMode) {
                                ExpirationMode.NEVER -> {
                                    task = queue.take()
                                    isBusy = true
                                    numBusyWorkers.incrementAndGet()
                                }
                                ExpirationMode.ON_IDLE_TIMOUT -> {
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
                                    numBusyWorkers.incrementAndGet()
                                }

                            }

                        } else {
                            if (!isBusy) {
                                isBusy = true
                                numBusyWorkers.incrementAndGet()
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
                workersLock.lock()
                try {
                    workers.remove(this)
                    // q: lock 안에 있어야 하는 경우는 언제이지? idleTimeout 할 때 decrement 하는걸 락 안에 둬야하는 경우도 설명했었음.
                    numWorkers.decrementAndGet()
                    if (isBusy) {
                        numBusyWorkers.decrementAndGet()
                    }
                    if (workers.isEmpty() && queue.isNotEmpty()) {
                        if (queue.any { it != SHUTDOWN_TASK }) {
                            addWorkersIfNecessary()
                        }
                    }
                } finally {
                    workersLock.unlock()
                }
                logger.debug("{} ({}) is terminated", Thread.currentThread().name, expirationMode)
            }
        }

        fun join() {
            do {
                try {
                    thread.join()
                } catch (_: InterruptedException) {}
                // q: 얠 붙였던건 interrupt 될수도 있으니까 맞나
            } while (thread.isAlive)
        }
    }

    enum class ExpirationMode {
        NEVER,
        ON_IDLE_TIMOUT
    }
}