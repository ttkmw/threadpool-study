package draft._8

import com.google.common.base.Preconditions.checkArgument
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class ThreadPoolBuilder8(maxNumWorkers: Int) {
    private val maxNumWorkers: Int
    private var minNumWorkers: Int = 0
    private var idleTimeoutNanos: Long = 0

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
        // q: 왜 여기에 따로 checkArgument 를 해야했지?
        return idleTimeout(idleTimeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    fun build(): ThreadPool8 {
        return ThreadPool8(
            minNumWorkers = minNumWorkers,
            maxNumWorkers = maxNumWorkers,
            idleTimeoutNanos = idleTimeoutNanos
        )
    }
}