import com.google.common.base.Preconditions.checkState
import org.slf4j.LoggerFactory
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer

/*
* 문제점
* 태스크를 실행할 때 스레드가 있다고 판단해서 스레드를 추가하지 않았다
* 태스크a를 큐에 넣었다
* 스레드를 종료했다.
* 태스크a가 실행되지 않는다. - 문제
*
* */
class ThreadPool(
    private val minNumWorkers: Int,
    private val maxNumWorkers: Int,
    private val idleTimeoutNanos: Long,
    private val taskTimeoutNanos: Long,
    watchdogIntervalNanos: Long,
    private val queue: BlockingQueue<Runnable>,
    private val submissionHandler: TaskSubmissionHandler,
    private val exceptionHandler: TaskExceptionHandler,
) : Executor {



    companion object {
        private val SHUTDOWN_TASK = Runnable { }

        private val logger = LoggerFactory.getLogger(ThreadPool::class.java)
        val TASK_NOT_STARTED_MARKER = (0).toLong()

        fun of(maxNumWorkers: Int): ThreadPool {
            return builder(maxNumWorkers).build()
        }

        fun of(minNumWorkers: Int, maxNumWorkers: Int): ThreadPool {
            return builder(maxNumWorkers).minNumWorkers(minNumWorkers).build()
        }

        fun builder(maxNumWorkers: Int): ThreadPoolBuilder {
            return ThreadPoolBuilder(maxNumWorkers)
        }
    }

    private val numBusyWorkers = AtomicInteger()
    private val numWorkers = AtomicInteger()
    var workers = HashSet<Worker>()
    private val shutdownState = AtomicReference(ShutdownState.NOT_SHUTDOWN)
    val workersLock = ReentrantLock()
    private var watchdog: Watchdog? = null

    init {
        if (taskTimeoutNanos != (0).toLong()) {
            watchdog = Watchdog(this, taskTimeoutNanos, watchdogIntervalNanos)
        }
    }
    override fun execute(task: Runnable) {
        if (!handleLateSubmission(task)) {
            return
        }

        if (!handleSubmission(task)) return

        addWorkerIfNecessary()

        if (isShutdown()) {
            this.queue.remove(task)
            val accepted = handleLateSubmission(task)
            assert(!accepted)
        }
    }



    private fun handleSubmission(task: Runnable): Boolean {
        val taskAction = submissionHandler.handleSubmission(task = task, threadPool = this)
        if (taskAction == TaskActions.ACCEPT) {
            this.queue.add(task)
            return true
        }
        taskAction.doAction(task)
        return false

    }

    private fun handleLateSubmission(task: Runnable): Boolean {
        if (!isShutdown()) {
            return true
        }
        val taskAction = submissionHandler.handleLateSubmission(task, this)
        checkState(taskAction != TaskAction.accept(), "Task Action must not be Accept")
        taskAction.doAction(task)
        return false
    }


    private fun addWorkerIfNecessary() {
        if (needsMoreWorker() != null) {
            workersLock.lock()
            var newWorkers: MutableList<Worker>? = null
            // try, finally를 newThread에만 걸어도 되는건지, needsMoreThreads까지 포함해야하는건지 궁금.
            try {

                while (!isShutdown()) {
                    val expirationMode = needsMoreWorker()
                    if (expirationMode != null) {
                        if (newWorkers == null) {
                            newWorkers = ArrayList()
                        }
                        newWorkers.add(newWorker(expirationMode))
                    } else {
                        break
                    }
                }
            } finally {
                workersLock.unlock()
            }

            if (newWorkers != null) {
                watchdog?.start()
                newWorkers.forEach(Worker::start)
            }
        }
    }

    private fun newWorker(expirationMode: ExpirationMode): Worker {
        numWorkers.incrementAndGet()
        numBusyWorkers.incrementAndGet()
        val newWorker = Worker(expirationMode)
        workers.add(newWorker)
        return newWorker
    }

    fun forEachWorker(consumer: Consumer<Worker>) {
        workers.forEach(consumer)
    }

    /**
     * Returns the worker type if more worker is needed to handle newly submitted task.
     * {@code null} is returned if no worker is needed
     */
    private fun needsMoreWorker(): ExpirationMode? {
        val numBusyWorkers = this.numBusyWorkers.get();
        val numWorkers = numWorkers.get()
        // Needs more threads if there are too few threads; we need at least `minNumThreads` threads
        if (numWorkers < minNumWorkers) {
            return ExpirationMode.NEVER
        }
        // Needs more threads if all threads are busy
        if (numBusyWorkers >= numWorkers) {
            // But we shouldn't create more threads than `maxNumThreads`
            if (numBusyWorkers < maxNumWorkers) {
                return if (idleTimeoutNanos > 0) ExpirationMode.ON_IDLE else ExpirationMode.NEVER
            }
        }

        return null


    }

    fun isShutdown() = shutdownState.get() != ShutdownState.NOT_SHUTDOWN
    fun shutdown() {
        doShutdown(false)
    }

    fun shutdownNow(): List<*> {
        doShutdown(true)
        val unprocessedTasks: MutableList<Runnable> = ArrayList(queue.size)
        while (true) {
            val task = queue.poll() ?: break

            if (task != SHUTDOWN_TASK) {
                unprocessedTasks.add(task)
            }
        }
        if (queue.isNotEmpty()) {
            for (task in queue.toTypedArray()) {
                if (task != SHUTDOWN_TASK && !unprocessedTasks.contains(task)) {
                    unprocessedTasks.add(task)
                }
            }
        }
        return unprocessedTasks
    }

    private fun doShutdown(interrupt: Boolean) {
        var needsShutdownTasks = false
        if (interrupt) {
            if (shutdownState.compareAndSet(ShutdownState.NOT_SHUTDOWN, ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT)) {
                needsShutdownTasks = true
            } else {
                shutdownState.compareAndSet(ShutdownState.SHUTTING_DOWN_WITHOUT_INTERRUPT, ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT)
                logger.debug("shutdownNow() is called while shutdown() is in progress")
            }
        } else {
            if (shutdownState.compareAndSet(ShutdownState.NOT_SHUTDOWN, ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT)) {
                needsShutdownTasks = true
            }
        }

        if (needsShutdownTasks) {
            for (i in 0 ..< maxNumWorkers) {
                queue.add(SHUTDOWN_TASK)
            }
        }

        while (true) {
            val workers = arrayOfNulls<Worker>(this.workers.size)
            workersLock.lock()
            try {
                this.workers.toArray(workers)
            } finally {
                workersLock.unlock()
            }

            if (workers.isEmpty()) {
                break
            }

            watchdog?.interrupt(AbstractWorker.InterruptReason.SHUTDOWN)
            if (interrupt) {
                for (w in workers) {
                    if (w == null) {
                        continue
                    }
                    w.interrupt(AbstractWorker.InterruptReason.SHUTDOWN)
                }
            }

            watchdog?.join()
            for (worker in workers) {
                if (worker == null) {
                    continue
                }
                worker.join()
            }
        }

        shutdownState.set(ShutdownState.SHUTDOWN)
    }

    inner class Worker(private val expirationMode: ExpirationMode): AbstractWorker() {
        @Volatile
        private var taskStartTimeNanos: Long = TASK_NOT_STARTED_MARKER

        override fun work() {
            logger.debug("Started an new thread: {}, (expiration mode: {})", threadName(), expirationMode)

            var isBusy = true
            var lastRunTimeNanos = System.nanoTime()
            try {
                while (true) {

                    var task: Runnable? = null
                    try {
                        clearInterrupt()
                        if (shutdownState.get() == ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT) {
                            logger.debug("Terminating a worker by an interrupt {}", threadName())
                            break
                        }
                        task = this@ThreadPool.queue.poll()
                        if (task != null) {
                            if (!isBusy) {
                                isBusy = true
                                numBusyWorkers.incrementAndGet()
                            }
                        } else {
                            if (isBusy) {
                                isBusy = false
                                numBusyWorkers.decrementAndGet()
                            }

                            when (expirationMode) {
                                ExpirationMode.NEVER -> {
                                    task = this@ThreadPool.queue.take()
                                }

                                ExpirationMode.ON_IDLE -> {
                                    val waitTimeoutNanos = idleTimeoutNanos - (System.nanoTime() - lastRunTimeNanos)
                                    if (waitTimeoutNanos <= 0) {
                                        logger.debug(
                                            "Terminating an idle worker {}",
                                            "${threadName()} hit by idle timeout"
                                        )
                                        break
                                    }

                                    task = this@ThreadPool.queue.poll(waitTimeoutNanos, TimeUnit.NANOSECONDS)
                                    if (task == null) {
                                        logger.debug(
                                            "Terminating an idle worker {}",
                                            "${threadName()} hit by idle timeout"
                                        )
                                        break
                                    }
                                }
                            }

                            isBusy = true
                            numBusyWorkers.incrementAndGet()
                        }

                        if (task == SHUTDOWN_TASK) {
                            logger.debug("Terminating worker with a posion pill {}", threadName())
                            break
                        } else {
                            try {
                                setTaskStartTimeNanos()
                                task.run()
                            } finally {
                                clearTaskStartTimeNanos()
                                lastRunTimeNanos = System.nanoTime()
                            }
                        }
                    } catch (cause: InterruptedException) {
                        if (shutdownState.get() == ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT) {
                            logger.debug("Terminating a worker by an interrupt {}", threadName())
                            break
                        } else if (logger.isTraceEnabled) {
                            logger.trace("Ignoring an interrupt that is not triggered by shutdownNow()", cause)
                        } else {
                            logger.debug("Ignoring an interrupt that is not triggered by shutdownNow()")
                        }
                    } catch (cause: Throwable) {
                        if (task != null) {
                            try {
                                exceptionHandler.handleTaskException(
                                    task = task,
                                    cause = cause,
                                    threadPool = this@ThreadPool
                                )
                            } catch (t2: Throwable) {
                                t2.addSuppressed(cause)
                                logger.warn("unexpected error occurred from task exception handler:", t2)
                            }
                        } else {
                            logger.warn("unexpected exception:", cause)
                        }
                    }
                }
            } finally {
                workersLock.lock()
                try {
                    workers.remove(this)
                    numWorkers.decrementAndGet()
                    numBusyWorkers.decrementAndGet() // Was busy handling the 'SHUTDOWN_TASK'

                    if (workers.isEmpty() && this@ThreadPool.queue.isNotEmpty() && shutdownState.get() != ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT) {
                        for (task in this@ThreadPool.queue) {
                            if (task != SHUTDOWN_TASK) {
                                // We found the situation when
                                // - there are no active threads available and
                                // - there are tasks in the queue
                                // Start a new thread so that it's picked up
                                addWorkerIfNecessary()
                                break
                            }
                        }
                    }
                } finally {
                    workersLock.unlock()
                }
                logger.debug("A worker has been terminated {}, (expiration mode: {})", threadName(), expirationMode)
            }
        }

        private fun setTaskStartTimeNanos() {
            val nanoTime = System.nanoTime()
            taskStartTimeNanos = if (nanoTime == TASK_NOT_STARTED_MARKER) 1 else nanoTime
            val interruptedReason = interrupted()
            if (interruptedReason == InterruptReason.SHUTDOWN) {
                interrupt(InterruptReason.SHUTDOWN)
            } else {
                assert(interruptedReason == null || interruptedReason == InterruptReason.WATCHDOG)
            }
        }

        private fun clearTaskStartTimeNanos() {
            taskStartTimeNanos = TASK_NOT_STARTED_MARKER
        }

        fun taskStartTimeNanos(): Long {
            return taskStartTimeNanos
        }
    }
    enum class ExpirationMode {
        /**
         * the worker that never gets terminated
         */
        NEVER,

        /**
         * the worker that can be terminated due to idle timeout
         */
        ON_IDLE
    }

    enum class ShutdownState {
        NOT_SHUTDOWN,
        SHUTTING_DOWN_WITHOUT_INTERRUPT,
        SHUTTING_DOWN_WITH_INTERRUPT,
        SHUTDOWN
    }
}