import com.google.common.base.Preconditions.checkArgument
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class ThreadPoolBuilder(private val maxNumWorkers: Int) {
    private var minNumWorkers: Int = 0
    private var idleTimeoutNanos: Long = 0

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

    fun build(): ThreadPool {
        return ThreadPool(
            minNumWorkers = minNumWorkers,
            maxNumWorkers = maxNumWorkers,
            idleTimeoutNanos = idleTimeoutNanos
        )
    }

}