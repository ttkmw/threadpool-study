package draft._8

import TaskAction
import com.google.common.base.Preconditions.checkState
import org.slf4j.LoggerFactory
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.log

// shutdownNow
// watchdog
// executorService
class ThreadPool8(
    private val minNumWorkers: Int,
    private val maxNumWorkers: Int,
    private val idleTimeoutNanos: Long,
    private val taskTimeoutNanos: Long,
    private val watchdogIterationDurationMillis: Long,
    private val watchdogIterationDurationNanos: Int,
    private val queue: BlockingQueue<Runnable>,
    private val submissionHandler: TaskSubmissionHandler8,
    private val exceptionHandler: TaskExceptionHandler8
) : Executor {
    val workers = HashSet<Worker>()

    private val numWorkers = AtomicInteger()
    private val numBusyWorkers = AtomicInteger()

    private val workersLock = ReentrantLock()

    private val shutdownState = AtomicReference(ShutdownState.NOT_SHUTDOWN)
    private val started = AtomicBoolean()

    private val watchdog: Watchdog? = if (taskTimeoutNanos == (0).toLong()) null else Watchdog()

    companion object {
        private val logger = LoggerFactory.getLogger(ThreadPool8::class.java)
        private val SHUTDOWN_TASK = Runnable { }
        private val TASK_NOT_STARTED_NANOS = (0).toLong()

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
        if (!handleLateSubmission(task)) return

        if (!handleSubmission(task)) return

        addWorkersIfNecessary()

        if (isShutdown()) {
            queue.remove(task)
            val accepted = handleLateSubmission(task)
            assert(!accepted)
        }
    }

    private fun handleSubmission(task: Runnable): Boolean {
        val taskAction = submissionHandler.handleSubmission(task = task, threadPool = this)
        if (taskAction == TaskAction8.accept()) {
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
        val taskAction = submissionHandler.handleLateSubmission(task, this)
        checkState(taskAction != TaskAction.accept(), "task Action must not be Accept")
        taskAction.doAction(task)
        return false
    }

    private fun addWorkersIfNecessary() {
        if (needsMoreWorkers() != null) {
            workersLock.lock()
            var newWorkers: MutableList<Worker>? = null
            try {
                while (!isShutdown()) {
                    val expirationMode = needsMoreWorkers() ?: break
                    if (newWorkers == null) {
                        newWorkers = ArrayList()
                    }
                    newWorkers.add(newWorker(expirationMode))
                }
            } finally {
                workersLock.unlock()
            }

            if (newWorkers != null) {
                // q8: 왜 여기서 start하는지 모름
                watchdog?.start()
                newWorkers.forEach { it.start() }
            }


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
        doShutdown(false)
    }

    fun shutdownNow(): List<Runnable> {
        doShutdown(true)
        val unprocessedTasks = ArrayList<Runnable>()
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

    private fun isShutdown(): Boolean {
        return shutdownState.get() != ShutdownState.NOT_SHUTDOWN
    }

    private fun doShutdown(interrupt: Boolean) {
        logger.debug("shutdown thread pool is started")
        var needsShutdownTasks = false
        if (interrupt) {
            if (shutdownState.compareAndSet(ShutdownState.NOT_SHUTDOWN, ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT)) {
                needsShutdownTasks = true
            } else {
                shutdownState.compareAndSet(ShutdownState.SHUTTING_DOWN_WITHOUT_INTERRUPT, ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT)
                logger.debug("shutdownNow() is called while shutdown is in progress")
            }
        } else {
            if (shutdownState.compareAndSet(ShutdownState.NOT_SHUTDOWN, ShutdownState.SHUTTING_DOWN_WITHOUT_INTERRUPT)) {
                needsShutdownTasks = true
            }
        }

        if (needsShutdownTasks) {
            for (i in 0..<maxNumWorkers) {
                queue.add(SHUTDOWN_TASK)
            }
        }

        // q8: 얘 왜붙였었지
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

            // q8: 이거 호출되는거때매 다 끝나지도 않았는데 watchdog이 꺼져서 shutdown을 계속 기다리게됨. 그래도 되나?
            watchdog?.interrupt(InterruptedReason.SHUTDOWN)
            if (interrupt) {
                for (w in workers) {
                    w.interrupt(InterruptedReason.SHUTDOWN)
                }
            }

            watchdog?.join()
            for (w in workers) {
                w.join()
            }
        }
        shutdownState.set(ShutdownState.TERMINATED)
    }

    abstract inner class AbstractWorker {
        private val thread = Thread(::work)
        private val started = AtomicBoolean()
        @Volatile
        private var interruptedReason: InterruptedReason? = null
        fun start() {
            if (started.compareAndSet(false, true)) {
                thread.start()
            }
        }
        fun join() {
            do {
                try {
                    thread.join()
                } catch (_: InterruptedException) {
                }
                // q8: 얠 붙였던건 interrupt 될수도 있으니까 맞나
            } while (thread.isAlive)
        }

        fun interrupted(): InterruptedReason? {
            val interrupted = Thread.interrupted()
            val interruptedReason = this.interruptedReason
            this.interruptedReason = null
            if (interrupted) {
                return interruptedReason
            }
            return null
        }

        fun interrupt(interruptedReason: InterruptedReason) {
            this.interruptedReason = interruptedReason
            thread.interrupt()
        }
        abstract fun work()
        fun clearInterrupt() {
            interruptedReason = null
            Thread.interrupted()
        }
    }

    enum class InterruptedReason {
        SHUTDOWN,
        WATCHDOG
    }

    inner class Watchdog: AbstractWorker() {
        override fun work() {
            logger.debug("Started a watchdog {}", Thread.currentThread().name)
            try {
                while (!isShutdown()) {
                    try {
                        Thread.sleep(watchdogIterationDurationMillis, watchdogIterationDurationNanos)
                    } catch (_: InterruptedException) {
                        continue
                    }
                    for (w in workers) {
                        val taskStartTimeNanos = w.taskStartTimeNanos()
                        // q8: 이거 왜하는지 모름
                        if (taskStartTimeNanos == TASK_NOT_STARTED_NANOS) {
                            break
                        }
                        val runningTime = System.nanoTime() - taskStartTimeNanos
                        if (taskTimeoutNanos < runningTime) {
                            w.interrupt(InterruptedReason.WATCHDOG)
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.warn("Unexpected exception", t)
            } finally {
                logger.debug("A watchdog has been terminated {}", Thread.currentThread().name)
            }
        }

    }

    inner class Worker(private val expirationMode: ExpirationMode): AbstractWorker() {
        @Volatile
        private var taskStartTimeNanos = TASK_NOT_STARTED_NANOS

        override fun work() {
            val threadName = Thread.currentThread().name
            logger.debug("Started a new thread {} ({})", threadName, expirationMode)
            var isBusy = true
            var lastTimeoutNanos = System.nanoTime()
            try {
                while (true) {
                    var task: Runnable? = null
                    try {
                        // q8: 이거 왜하는지 모름
                        clearInterrupt()
                        if (shutdownState.get() == ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT) {
                            logger.debug("Terminating a worker by an interrupt {}", threadName)
                            break
                        }
                        task = queue.poll()
                        if (task == null) {
                            if (isBusy) {
                                isBusy = false
                                numBusyWorkers.decrementAndGet()
                            }
                            when (expirationMode) {
                                ExpirationMode.NEVER -> {
                                    task = queue.take()
                                }

                                ExpirationMode.ON_IDLE_TIMOUT -> {
                                    val waitTimeNanos = idleTimeoutNanos - (System.nanoTime() - lastTimeoutNanos)
                                    if (waitTimeNanos < 0) {
                                        logger.debug(
                                            "{} stops doing work because {} haven't work too long time",
                                            threadName,
                                            threadName
                                        )
                                        break
                                    }
                                    task = queue.poll(waitTimeNanos, TimeUnit.NANOSECONDS)
                                    if (task == null) {
                                        logger.debug(
                                            "{} stops doing work because there is no work for some time",
                                            threadName
                                        )
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
                            logger.debug(
                                "{} received a command that 'do not work'. {} stops doing work",
                                threadName,
                                threadName
                            )
                            break
                        } else {

                            try {
                                setTaskStartTimeNanos()
                                task.run()
                            } finally {
                                clearTaskStartTimeNanos()
                                lastTimeoutNanos = System.nanoTime()

                            }
                        }
                    } catch (cause: InterruptedException) {
                        if (shutdownState.get() == ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT) {
                            logger.debug("${Thread.currentThread().name} is shutting down because it received command that stop work immediately")
                            break
                        } else if (logger.isTraceEnabled) {
                            logger.trace("Ignoring an interrupt except command that stop work immediately", cause)
                        } else {
                            logger.debug("Ignoring an interrupt except command that stop work immediately")
                        }
                    } catch (t: Throwable) {
                        if (task != null) {
                            try {
                                exceptionHandler.handleException(task, t, this@ThreadPool8)
                            } catch (t2: Throwable) {
                                t2.addSuppressed(t)
                                logger.warn("unexpected error occurred from task exception handler: ", t2)
                            }
                        } else {
                            logger.warn("unexpected error occurred: ",t)
                        }
                    }
                }
            } finally {
                workersLock.lock()
                try {
                    workers.remove(this)
                    // q: lock 안에 있어야 하는 경우는 언제이지? idleTimeout 할 때 decrement 하는걸 락 안에 둬야하는 경우도 설명했었음.
                    numWorkers.decrementAndGet()
                    // q: isBusy 둬야할 것 같은데 왜 빠졌을까?
                    if (isBusy) {
                        numBusyWorkers.decrementAndGet()
                    }
                    if (workers.isEmpty() && queue.isNotEmpty() && shutdownState.get() != ShutdownState.SHUTTING_DOWN_WITH_INTERRUPT) {
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

        private fun clearTaskStartTimeNanos() {
            taskStartTimeNanos = TASK_NOT_STARTED_NANOS
        }

        private fun setTaskStartTimeNanos() {
            val nanoTime = System.nanoTime()
            taskStartTimeNanos = if (nanoTime == TASK_NOT_STARTED_NANOS) 1 else nanoTime
            val interruptedReason = interrupted()
            if (interruptedReason == InterruptedReason.SHUTDOWN) {
                interrupt(InterruptedReason.SHUTDOWN)
            } else{
                assert(interruptedReason == null || interruptedReason == InterruptedReason.WATCHDOG)
            }
        }

        fun taskStartTimeNanos() = taskStartTimeNanos
    }


    enum class ExpirationMode {
        NEVER,
        ON_IDLE_TIMOUT
    }

    enum class ShutdownState {
        NOT_SHUTDOWN,
        SHUTTING_DOWN_WITHOUT_INTERRUPT,
        SHUTTING_DOWN_WITH_INTERRUPT,
        TERMINATED
    }
}