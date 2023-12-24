import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration

/*
* 문제점
* 스레드 갯수를 정하는게 정적이다.
* */
class ThreadPool(private val maxNumThreads: Int, idleTimeout: Duration): Executor {

    private val SHUTDOWN_TASK = Runnable {  }
    private val numActiveThreads = AtomicInteger()
    private val numThreads = AtomicInteger()
    var threads = HashSet<Thread>()
    private val queue = LinkedTransferQueue<Runnable>()
    private val shuttingDown = AtomicBoolean()
    val threadLock = ReentrantLock()
    private val idleTimeoutNanos = idleTimeout.inWholeNanoseconds
    override fun execute(command: Runnable) {
        if (shuttingDown.get()) {
            throw RejectedExecutionException()
        }

        queue.add(command)
        addThreadIfNecessary()

        if (shuttingDown.get()) {
            queue.remove(command)
            throw RejectedExecutionException()
        }
    }

    private fun addThreadIfNecessary() {
        if (needsMoreThreads()) {
            threadLock.lock()
            var thread: Thread? = null
            // try, finnally를 newThread에만 걸어도 되는건지, needsMoreThreads까지 포함해야하는건지 궁금.
            try {
                if (needsMoreThreads()) {
                    thread = newThread()
                }
            } finally {
                threadLock.unlock()
            }

            thread?.start()
        }
    }

    private fun newThread(): Thread {
        numThreads.incrementAndGet()
        numActiveThreads.incrementAndGet()
        val newThread = Thread {
            var isActive = true
            var lastRunTimeNanos = System.nanoTime()
            try {
                while (true) {
                    try {
                        var task = queue.poll()
                        if (task != null) {
                            if (!isActive) {
                                isActive = true
                                numActiveThreads.incrementAndGet()
                            }
                        } else {
                            if (isActive) {
                                isActive = false
                                numActiveThreads.decrementAndGet()
                            }
                            val waitTimeoutNanos = idleTimeoutNanos - (System.nanoTime() - lastRunTimeNanos)
                            task = queue.poll(waitTimeoutNanos, TimeUnit.NANOSECONDS)
                            if (task == null) {
                                break
                            }
                            isActive = true
                            numActiveThreads.incrementAndGet()
                        }

                        if (task == SHUTDOWN_TASK) {
                            break
                        } else {
                            try {
                                task.run()
                            } finally {
                                lastRunTimeNanos = System.nanoTime()
                            }
                        }
                    } catch (t: Throwable) {
                        if (t !is InterruptedException) {
                            println("unexpected exception thrown")
                            t.printStackTrace()
                        }
                    }
                }
            } finally {
                threadLock.lock()
                try {
                    threads.remove(Thread.currentThread())
                    numThreads.decrementAndGet()
                    if (isActive) {
                        numActiveThreads.decrementAndGet()
                    }

                    if (threads.isEmpty() && queue.isNotEmpty()) {
                        for (task in queue) {
                            if (task != SHUTDOWN_TASK) {
                                println("!! Found there is remaining task but there is no thread to pick up the task")
                            }
                        }
                    }
                } finally {
                    threadLock.unlock()
                }

            }
            println("shutting down - ${Thread.currentThread().name}")
        }
        threads.add(newThread)
        return newThread
    }

    private fun needsMoreThreads(): Boolean {
        val numActiveThreads = this.numActiveThreads.get();
        return numActiveThreads < maxNumThreads && numActiveThreads >= threads.size
    }

    fun shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            for (i in 0 ..< maxNumThreads) {
                queue.add(SHUTDOWN_TASK)
            }
        }
        while (true) {
            val threads = arrayOfNulls<Thread>(this.threads.size)
            threadLock.lock()
            try {
                this.threads.toArray(threads)
            } finally {
                threadLock.unlock()
            }

            if (threads.isEmpty()) {
                break
            }


            for (thread in threads) {
                if (thread == null) {
                    continue
                }

                do {
                    try {
                        thread.join()
                    } catch (_: InterruptedException) {

                    }

                } while (thread.isAlive)
            }
        }
    }
}