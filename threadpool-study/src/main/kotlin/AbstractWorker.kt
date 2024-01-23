import java.util.concurrent.atomic.AtomicBoolean

abstract class AbstractWorker {
    private val thread: Thread
    private val started = AtomicBoolean()

    @Volatile
    private var interruptReason: InterruptReason? = null

    init {
        thread = Thread(::work)
    }
    fun start() {
        if (started.compareAndSet(false, true)) {
            thread.start()
        }
    }
    fun threadName() = thread.name

    fun join() {
        while (thread.isAlive) {
            try {
                thread.join()
            } catch (_: InterruptedException) {

            }
        }
    }

    fun interrupt(reason: InterruptReason) {
        interruptReason = reason
        thread.interrupt()
    }

    fun interrupted(): InterruptReason? {
        val interrupted = Thread.interrupted()
        val interruptReason = this.interruptReason
        this.interruptReason = null
        return if (interrupted) {
            return interruptReason
        } else null
    }

    abstract fun work()
    fun clearInterrupt() {
        interruptReason = null
        Thread.interrupted()
    }

    enum class InterruptReason {
        SHUTDOWN,
        WATCHDOG
    }
}