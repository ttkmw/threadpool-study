import com.google.common.base.Preconditions.checkArgument
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class ThreadPoolBuilder(private val maxNumWorkers: Int) {

    companion object {
        private val DEFAULT_WATCHDOG_INTERVAL_SECONDS = (1).toLong()
    }


    private var minNumWorkers: Int = 0
    private var idleTimeoutNanos: Long = 0
    private var queue: BlockingQueue<Runnable>? = null
    private var submissionHandler: TaskSubmissionHandler = TaskSubmissionHandler.ofDefault()
    private var exceptionHandler: TaskExceptionHandler = TaskExceptionHandler.ofDefault()
    private var taskTimeoutNanos = (0).toLong() // 0 means disabled
    private var watchdogIntervalNanos = TimeUnit.SECONDS.toNanos(DEFAULT_WATCHDOG_INTERVAL_SECONDS)
    init {
        checkArgument(maxNumWorkers > 0 , "maxNumWorkers: %s (expected: > 0)")
    }

    fun minNumWorkers(minNumWorkers: Int): ThreadPoolBuilder {
        checkArgument(minNumWorkers in 0..maxNumWorkers, "minNumWorkers: %s (expected: [0, minNumWorkers (%s)]", minNumWorkers, maxNumWorkers)
        this.minNumWorkers = minNumWorkers
        return this
    }

    fun idleTimeout(idleTimeout: Long, unit: TimeUnit): ThreadPoolBuilder {
        checkArgument(idleTimeout >= 0, "idleTimeout: %s (expected: >= 0)")
        this.idleTimeoutNanos = unit.toNanos(idleTimeout)
        return this
    }

    fun idleTimeout(idleTimeout: Duration): ThreadPoolBuilder {
        checkArgument(!idleTimeout.isNegative(), "idleTimeout: %s (expected: >= 0)")
        return idleTimeout(idleTimeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    fun queue(queue: BlockingQueue<Runnable>): ThreadPoolBuilder {
        this.queue = queue
        return this
    }

    fun submissionHandler(submissionHandler: TaskSubmissionHandler): ThreadPoolBuilder {
        this.submissionHandler = submissionHandler
        return this
    }

    fun exceptionHandler(exceptionHandler: TaskExceptionHandler): ThreadPoolBuilder {
        this.exceptionHandler = exceptionHandler
        return this
    }

    fun taskTimeout(taskTimeout: Long, unit: TimeUnit): ThreadPoolBuilder {
        checkArgument(taskTimeout >= 0, "taskTimeout: %s (expected: >= 0)")
        taskTimeoutNanos = unit.toNanos(taskTimeout)
        return this
    }

    fun taskTimeout(taskTimeout: Duration): ThreadPoolBuilder {
        checkArgument(!taskTimeout.isNegative(), "taskTimeout: %s (expected: >= 0)")
        return taskTimeout(taskTimeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    fun watchdogInterval(watchdogInterval: Long, unit: TimeUnit): ThreadPoolBuilder {
        checkArgument(watchdogInterval > 0, "watchdogInterval: %s (expected: >= 0)")
        watchdogIntervalNanos = unit.toNanos(watchdogInterval)
        return this
    }

    fun watchdogInterval(watchdogInterval: Duration): ThreadPoolBuilder {
        checkArgument(watchdogInterval.isPositive(), "watchdogInterval: %s (expected: >= 0)")
        return watchdogInterval(watchdogInterval.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    fun build(): ThreadPool {
        val queue = this.queue?: LinkedBlockingQueue()
        return ThreadPool(
            minNumWorkers = minNumWorkers,
            maxNumWorkers = maxNumWorkers,
            idleTimeoutNanos = idleTimeoutNanos,
            taskTimeoutNanos = taskTimeoutNanos,
            watchdogIntervalNanos = watchdogIntervalNanos,
            queue = queue,
            submissionHandler = submissionHandler,
            exceptionHandler = exceptionHandler
        )
    }

}