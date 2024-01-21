import com.google.common.base.Preconditions.checkArgument
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class ThreadPoolBuilder(private val maxNumWorkers: Int) {
    private var minNumWorkers: Int = 0
    private var idleTimeoutNanos: Long = 0
    private var queue: BlockingQueue<Runnable>? = null
    private var submissionHandler: TaskSubmissionHandler = TaskSubmissionHandler.ofDefault()
    private var exceptionHandler: TaskExceptionHandler = TaskExceptionHandler.ofDefault()
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

    fun build(): ThreadPool {
        val queue = this.queue?: LinkedBlockingQueue()
        return ThreadPool(
            minNumWorkers = minNumWorkers,
            maxNumWorkers = maxNumWorkers,
            idleTimeoutNanos = idleTimeoutNanos,
            queue = queue,
            submissionHandler = submissionHandler,
            exceptionHandler = exceptionHandler
        )
    }

}