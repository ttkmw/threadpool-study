package draft._8

import com.google.common.base.Preconditions.checkArgument
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class ThreadPoolBuilder8(maxNumWorkers: Int) {
    private val maxNumWorkers: Int
    private var minNumWorkers: Int = 0
    private var idleTimeoutNanos: Long = 0
    private var taskTimeoutNanos: Long = 0
    private var watchdogIterationDurationMillis: Long = TimeUnit.SECONDS.toMillis(10)
    private var watchdogIterationDurationNanos: Int = TimeUnit.NANOSECONDS.toNanos(0).toInt()
    private var queue: BlockingQueue<Runnable>? = null
    private var submissionHandler: TaskSubmissionHandler8 = TaskSubmissionHandler8.ofDefault()
    private var exceptionHandler: TaskExceptionHandler8 = TaskExceptionHandler8.ofDefault()

    init {
        checkArgument(maxNumWorkers > 0, "maxNumWorkers: %s (expected > 0)")
        this.maxNumWorkers = maxNumWorkers
    }

    fun minNumWorkers(minNumWorkers: Int): ThreadPoolBuilder8 {
        checkArgument(minNumWorkers in 0..maxNumWorkers, "minNumWorkers: %s, (0 <= expected <= maxNumWorkers)")
        this.minNumWorkers = minNumWorkers
        return this
    }

    fun idleTimeout(idleTimeout: Long, unit: TimeUnit): ThreadPoolBuilder8 {
        checkArgument(idleTimeout >= 0, "idleTimeout: %s, (0 <= expected)")
        this.idleTimeoutNanos = unit.toNanos(idleTimeout)
        return this
    }

    fun idleTimeout(idleTimeout: Duration): ThreadPoolBuilder8 {
        // q8: 왜 여기에 따로 checkArgument 를 해야했지?
        return idleTimeout(idleTimeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    fun taskTimeout(taskTimeout: Long, unit: TimeUnit): ThreadPoolBuilder8 {
        checkArgument(taskTimeout >= 0, "taskTimeout: %s, (0 <= expected)")
        this.taskTimeoutNanos = unit.toNanos(taskTimeout)
        return this
    }

    fun taskTimeout(taskTimeout: Duration): ThreadPoolBuilder8 {
        // q8: 왜 여기에 따로 checkArgument 를 해야했지?
        return taskTimeout(taskTimeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    fun watchdogIterationDuration(watchdogIterationDuration: Long, unit: TimeUnit): ThreadPoolBuilder8 {
        checkArgument(watchdogIterationDuration >= 0, "watchdogIterationDuration: %s, (0 <= expected)")
        val totalNanos = unit.toNanos(watchdogIterationDuration)
        this.watchdogIterationDurationMillis = totalNanos / TimeUnit.MILLISECONDS.toNanos(1)
        this.watchdogIterationDurationNanos = (totalNanos % TimeUnit.MILLISECONDS.toNanos(1)).toInt()
        return this
    }

    fun watchdogIterationDuration(watchdogIterationDuration: Duration): ThreadPoolBuilder8 {
        // q8: 왜 여기에 따로 checkArgument 를 해야했지?
        return taskTimeout(watchdogIterationDuration.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    fun queue(queue: BlockingQueue<Runnable>): ThreadPoolBuilder8 {
        this.queue = queue
        return this
    }

    fun submissionHandler(submissionHandler: TaskSubmissionHandler8): ThreadPoolBuilder8 {
        this.submissionHandler = submissionHandler
        return this
    }

    fun exceptionHandler(exceptionHandler8: TaskExceptionHandler8): ThreadPoolBuilder8 {
        this.exceptionHandler = exceptionHandler8
        return this
    }

    fun build(): ThreadPool8 {
        val queue = this.queue ?: LinkedTransferQueue() // q: 이거 왜 LinkedBlockingQueue 썼었지?
        return ThreadPool8(
            minNumWorkers = minNumWorkers,
            maxNumWorkers = maxNumWorkers,
            idleTimeoutNanos = idleTimeoutNanos,
            taskTimeoutNanos = taskTimeoutNanos,
            watchdogIterationDurationMillis = watchdogIterationDurationMillis,
            watchdogIterationDurationNanos = watchdogIterationDurationNanos,
            queue = queue,
            submissionHandler = submissionHandler,
            exceptionHandler = exceptionHandler
        )
    }
}