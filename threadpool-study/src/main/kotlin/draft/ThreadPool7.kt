package draft

import com.google.common.base.Preconditions.checkState
import org.slf4j.LoggerFactory
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

class ThreadPool7 internal constructor(
    private val minNumWorkers: Int,
    private val maxNumWorkers: Int,
    private val idleTimeoutNanos: Long,
    private val queue: BlockingQueue<Runnable>,
    private val submissionHandler: TaskSubmissionHandler7,
    private val exceptionHandler: TaskExceptionHandler7,
) : Executor {
    private val workers = HashSet<Worker>()
    private val numWorkers = AtomicInteger()
    private val numBusyWorkers = AtomicInteger()
    private val shutdownState = AtomicReference(ShutdownState.NOT_SHUTDOWN)
    private val workerLock = ReentrantLock()

    companion object {
        private val SHUTDOWN_TASK = Runnable { }

        private val logger = LoggerFactory.getLogger(ThreadPool7::class.java)

        fun of(maxNumWorkers: Int): ThreadPool7 {
            return builder(maxNumWorkers = maxNumWorkers)
                .build()
        }

        fun of(minNumWorkers: Int, maxNumWorkers: Int): ThreadPool7 {
            return builder(maxNumWorkers = maxNumWorkers)
                .minNumWorkers(minNumWorkers = minNumWorkers)
                .build()
        }

        fun builder(maxNumWorkers: Int): ThreadPoolBuilder7 {
            return ThreadPoolBuilder7(maxNumWorkers = maxNumWorkers)
        }
    }

    private fun newWorker(EXPIRATIONMODE: ExpirationMode): Worker {
        numWorkers.incrementAndGet()
        numBusyWorkers.incrementAndGet()
        val newWorker = Worker(EXPIRATIONMODE)

        // 이희승님은 여기서 lock 안걸었는데... 왜 lock 안거신지 모르겠음 락 걸어야할것같은데.
        workerLock.lock()
        try {
            workers.add(newWorker)
        } finally {
            workerLock.unlock()
        }
        return newWorker
    }

    override fun execute(task: Runnable) {


        if (!handleLateSubmission(task)) return

        if (!handleSubmission(task)) return

        // queue.add보다 먼저 호출하면 무슨 문제가 생겼는지 까먹음.
        addWorkersIfNecessary()

        if (isShutdown()) {
            queue.remove(task)
            val accepted = handleLateSubmission(task)
            assert(!accepted)
        }
    }



    private fun handleSubmission(task: Runnable): Boolean {
        val taskAction = submissionHandler.handleSubmission(task = task, numPendingTasks = queue.size)
        if (taskAction == TaskAction7.accept()) {
            queue.add(task)
            return true
        }

        taskAction.doAction(task)
        return false
    }

    private fun handleLateSubmission(task: Runnable): Boolean {
        if (!isShutdown()) {
            return true
        }
        val taskAction = submissionHandler.handleLateSubmission(task = task)
        checkState(
            taskAction != TaskAction.accept(),
            "taskSubmissionHandler.handleLateSubmission must not accept a task"
        )
        taskAction.doAction(task)
        return false
    }

    private fun addWorkersIfNecessary() {
        if (needsMoreWorker() != null) {
            workerLock.lock()
            var newWorkers: MutableList<Worker>? = null
            try {
                while (!isShutdown()) {
                    val workerType = needsMoreWorker() ?: break
                    if (newWorkers == null) {
                        newWorkers = mutableListOf()
                    }

                    newWorkers.add(newWorker(workerType))
                }

            } finally {
                workerLock.unlock()
            }

            newWorkers?.forEach { it.start() }
        }
    }

    private fun needsMoreWorker(): ExpirationMode? {
        val numWorkers = numWorkers.get()
        val numBusyWorkers = numBusyWorkers.get()
        if (numWorkers < minNumWorkers) {
            return ExpirationMode.NEVER
        }
        if (numBusyWorkers >= numWorkers && numWorkers < maxNumWorkers) {
            return if (idleTimeoutNanos > 0) ExpirationMode.ON_IDLE else ExpirationMode.NEVER
        }
        return null
    }

    private fun isShutdown() = shutdownState.get() != ShutdownState.NOT_SHUTDOWN

    fun shutdown() {
        doShutdown(false)
    }

    fun shutdownNow(): List<*> {
        doShutdown(true)
        val unprocessed = ArrayList<Runnable>(queue.size)
        while (true) {
            val task = queue.poll() ?: break
            if (task != SHUTDOWN_TASK) {
                unprocessed.add(task)
            }
        }

        if (queue.isNotEmpty()) {
            for (task in queue.toTypedArray()) {
                if (task != SHUTDOWN_TASK && !unprocessed.contains(task)) {
                    unprocessed.add(task)
                }
            }
        }
        return unprocessed
    }

    private fun doShutdown(interrupt: Boolean) {
        var needsShutdownTasks = false
        if (interrupt) {
            if (shutdownState.compareAndSet(ShutdownState.NOT_SHUTDOWN, ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT)) {
                needsShutdownTasks = true
            } else {
                shutdownState.compareAndSet(ShutdownState.SHUTTING_DOWN_WITHOUT_INTERRUPT, ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT)
                // 여기 로거?
            }
        } else {
            if (shutdownState.compareAndSet(ShutdownState.NOT_SHUTDOWN, ShutdownState.SHUTTING_DOWN_WITHOUT_INTERRUPT)) {
                needsShutdownTasks = true
            }
        }

        if (needsShutdownTasks) {
            for (worker in workers) {
                queue.add(SHUTDOWN_TASK)
            }
        }

        // 여기서 while true 하는 이유 생각 안남.

        while (true) {
            val workers: Array<Worker>
            workerLock.lock()
            try {
                workers = this.workers.toTypedArray()
            } finally {
                workerLock.unlock()
            }

            if (workers.isEmpty()) {
                break
            }

            if (interrupt) {
                for (w in workers) {
                    w.interrupt()
                }
            }

            for (worker in workers) {
                worker.join()
            }
        }

        shutdownState.set(ShutdownState.SHUTDOWN)
    }

    enum class ExpirationMode {
        NEVER,
        ON_IDLE
    }

    enum class ShutdownState {
        NOT_SHUTDOWN,
        SHUTTING_DOWN_WITHOUT_INTERRUPT,
        SHUTTING_DOWN_WITH_INTERRUPT,
        SHUTDOWN
    }

    inner class Worker(private val EXPIRATIONMODE: ExpirationMode) {
        private val thread = Thread(this::work)

        private fun work() {
            var isBusy = true
            val threadName = Thread.currentThread().name
            var lastRuntimeNanos = System.nanoTime()
            try {
                while (true) {
                    var task: Runnable? = null
                    try {
                        task = queue.poll()
                        if (task == null) {
                            if (isBusy) {
                                isBusy = false
                                numBusyWorkers.decrementAndGet()
                            }

                            when (EXPIRATIONMODE) {
                                ExpirationMode.NEVER -> {
                                    task = queue.take()
                                }

                                ExpirationMode.ON_IDLE -> {
                                    val waitTimeNanos = idleTimeoutNanos - (System.nanoTime() - lastRuntimeNanos)
                                    task = queue.poll(waitTimeNanos, TimeUnit.NANOSECONDS)
                                    if (waitTimeNanos <= 0 || task == null) {
                                        logger.warn("Terminating $threadName because it is idle")
                                        break
                                    }
                                }
                            }

                            isBusy = true
                            numBusyWorkers.incrementAndGet()
                        } else {
                            if (!isBusy) {
                                isBusy = true
                                numBusyWorkers.incrementAndGet()
                            }
                        }

                        if (task == SHUTDOWN_TASK) {
                            logger.warn("Terminating $threadName because it received poison pill")
                            break
                        } else {
                            task.run()
                            lastRuntimeNanos = System.nanoTime()
                        }

                    } catch (t: InterruptedException) {
                        if (shutdownState.get() == ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT) {
                            logger.warn("Terminating $threadName because it is interrupted")
                            break
                        } else if (logger.isTraceEnabled) {
                            logger.trace("Ignoring an interrupt that is not triggered by shutdownNow()", t)
                        } else {
                            logger.debug("Ignoring an interrupt that is not triggered by shutdownNow()")
                        }
                    } catch (t: Throwable) {
                        if (task != null) {
                            try {
                                exceptionHandler.handleException(
                                    task = task,
                                    cause = t,
                                    threadPool = this@ThreadPool7
                                )
                            } catch (t2: Throwable) {
                                t2.addSuppressed(t)
                                logger.warn("unexpected error occurred at task exception handler", t2)
                            }
                        } else {
                            logger.warn("unexpected error occurred", t)
                        }
                    }
                }
            } finally {
                workerLock.lock()
                try {
                    workers.remove(this)
                    // 얘네 이 락 안에서 해야하는거였는지 까먹음.
                    numWorkers.decrementAndGet()
                    if (isBusy) {
                        numBusyWorkers.decrementAndGet()
                    }

                    if (workers.isEmpty() && queue.isNotEmpty()) {
                        for (task in queue) {
                            if (task != SHUTDOWN_TASK) {
                                addWorkersIfNecessary()
                                break
                            }
                        }
                    }
                } finally {
                    workerLock.unlock()
                }

                logger.warn("$threadName is terminated")
            }
        }

        fun start() {
            thread.start()
        }

        fun join() {
            do {
                try {
                    thread.join()
                } catch (_: InterruptedException) {
                }
                //여기서 while을 붙인건 interrupt될까봐 그런거였나?
            } while (thread.isAlive)
        }

        fun interrupt() {
            thread.interrupt()
        }
    }
}