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
* 태스크를 실행할 때 스레드가 있다고 판단해서 스레드를 추가하지 않았다
* 태스크a를 큐에 넣었다
* 스레드를 종료했다.
* 태스크a가 실행되지 않는다. - 문제
*
* */
class ThreadPool(private val maxNumThreads: Int, idleTimeout: Duration): Executor {

    private val SHUTDOWN_TASK = Runnable {  }
    private val numBusyThreads = AtomicInteger()
    private val numThreads = AtomicInteger()
    var threads = HashSet<Thread>()
    private val queue = LinkedTransferQueue<Runnable>()
    private val shuttingDown = AtomicBoolean()
    val threadLock = ReentrantLock()
    private val idleTimeoutNanos = idleTimeout.inWholeNanoseconds
    override fun execute(task: Runnable) {
        if (shuttingDown.get()) {
            throw RejectedExecutionException()
        }

        queue.add(task)
        addThreadIfNecessary()

        if (shuttingDown.get()) {
            queue.remove(task)
            throw RejectedExecutionException()
        }
    }

    private fun addThreadIfNecessary() {
        if (needsMoreThreads()) {
            threadLock.lock()
            var thread: Thread? = null
            // try, finally를 newThread에만 걸어도 되는건지, needsMoreThreads까지 포함해야하는건지 궁금.
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
        numBusyThreads.incrementAndGet()
        val newThread = Thread {
            println("Started a new thread: ${Thread.currentThread().name}")
            var isBusy = true
            var lastRunTimeNanos = System.nanoTime()
            try {
                while (true) {
                    try {
                        var task = queue.poll()
                        if (task != null) {
                            if (!isBusy) {
                                isBusy = true
                                numBusyThreads.incrementAndGet()
                                println("${Thread.currentThread().name} busy")
                            }
                        } else {
                            if (isBusy) {
                                isBusy = false
                                numBusyThreads.decrementAndGet()
                                println("${Thread.currentThread().name} idle")
                            }
                            val waitTimeoutNanos = idleTimeoutNanos - (System.nanoTime() - lastRunTimeNanos)
                            if (waitTimeoutNanos <= 0) {
                                break
                            }

                            task = queue.poll(waitTimeoutNanos, TimeUnit.NANOSECONDS)
                            if (task == null) {
                                break
                            }
                            isBusy = true
                            numBusyThreads.incrementAndGet()
                            println("${Thread.currentThread().name} busy")
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
                    if (isBusy) {
                        numBusyThreads.decrementAndGet()
                        println("${Thread.currentThread().name} idle (timed out)")
                    }

                    if (threads.isEmpty() && queue.isNotEmpty()) {
                        for (task in queue) {
                            if (task != SHUTDOWN_TASK) {
                                // We found the situation when
                                // - there are no active threads available and
                                // - there are tasks in the queue
                                // Start a new thread so that it's picked up
                                addThreadIfNecessary()
                                break
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
        val numBusyThreads = this.numBusyThreads.get();
        return numBusyThreads >= numThreads.get() && numBusyThreads < maxNumThreads
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