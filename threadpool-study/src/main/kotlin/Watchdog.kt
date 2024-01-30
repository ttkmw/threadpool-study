import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit


class Watchdog(
    private val threadPool: ThreadPool,
    private val taskTimeoutNanos: Long,
    watchdogIntervalNanos: Long
) : AbstractWorker() {
    private val nanosPerMilli = TimeUnit.MILLISECONDS.toNanos(1)
    private val watchdogIntervalMills = watchdogIntervalNanos / nanosPerMilli
    private val watchdogIntervalRemainingNanos = (watchdogIntervalNanos % nanosPerMilli).toInt()

    companion object {
        private val logger = LoggerFactory.getLogger(Watchdog::class.java)
    }

    override fun work() {
        logger.debug("Started a watchdog {}", threadName())
        try {
            while (!threadPool.isShutdown()) {
                try {
                    Thread.sleep(watchdogIntervalMills, watchdogIntervalRemainingNanos)
                } catch (_: InterruptedException) {
                    continue
                }

                threadPool.forEachWorker { w ->
                    val taskStartTimeNanos = w.taskStartTimeNanos()
                    if (taskStartTimeNanos == ThreadPool.TASK_NOT_STARTED_MARKER) {
                        return@forEachWorker
                    }
                    if (System.nanoTime() - taskStartTimeNanos > taskTimeoutNanos) {
                        w.interrupt(InterruptReason.WATCHDOG)
                    }
                }
            }
        } catch (t: Throwable) {
            logger.warn("Unexpected exception", t)
        } finally {
            logger.debug("A watchdog has been terminated {}", threadName())
        }
    }
}