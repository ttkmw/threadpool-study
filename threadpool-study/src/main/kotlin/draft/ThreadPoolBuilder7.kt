package draft

import com.google.common.base.Preconditions.checkArgument
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ThreadPoolBuilder7(private val maxNumWorkers: Int) {
    private var minNumWorkers: Int = 0
    private var idleTimeoutNanos: Long = 0.toDuration(DurationUnit.NANOSECONDS).inWholeNanoseconds
    private var queue: BlockingQueue<Runnable>? = null
    private var taskSubmissionHandler: TaskSubmissionHandler7 = TaskSubmissionHandler7.ofDefault()

    init {
        checkArgument(maxNumWorkers > 0, "maxNumWorkers: $maxNumWorkers, expected > 0")
    }

    fun minNumWorkers(minNumWorkers: Int): ThreadPoolBuilder7 {
        checkArgument(minNumWorkers in 0 .. maxNumWorkers, "minNumWorkers: $minNumWorkers, expected between 0 and maxNumWorkers($maxNumWorkers)")
        this.minNumWorkers = minNumWorkers
        return this
    }

    fun idleTimeout(idleTimeout: Long, unit: TimeUnit): ThreadPoolBuilder7 {
        checkArgument(idleTimeout >= 0, "idleTimeoutNanos: $idleTimeout, expected >= 0")
        this.idleTimeoutNanos = unit.toNanos(idleTimeout)
        return this
    }

    fun idleTimeout(idleTimeout: Duration): ThreadPoolBuilder7 {
        return idleTimeout(idleTimeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    fun queue(queue: BlockingQueue<Runnable>): ThreadPoolBuilder7 {
        this.queue = queue
        return this
    }

    fun taskSubmissionHandler(taskSubmissionHandler7: TaskSubmissionHandler7): ThreadPoolBuilder7 {
        this.taskSubmissionHandler = taskSubmissionHandler7
        return this
    }

    fun build(): ThreadPool7 {
        val queue = queue?: LinkedBlockingQueue()
        return ThreadPool7(
            minNumWorkers = minNumWorkers,
            maxNumWorkers = maxNumWorkers,
            idleTimeoutNanos = idleTimeoutNanos,
            queue = queue,
            taskSubmissionHandler = taskSubmissionHandler
        )
    }
}