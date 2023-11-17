import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/*
* 문제점
* 스레드 갯수를 정하는게 정적이다.
* */
class ThreadPool(maxNumThreads: Int): Executor {

    private val SHUTDOWN_TASK = Runnable {  }
    private var maxNumThreads: Int
    private val numActiveThreads = AtomicInteger()
    private var threads = Collections.newSetFromMap(ConcurrentHashMap<Thread, Boolean>())
    private val queue = LinkedTransferQueue<Runnable>()
    private val started = AtomicBoolean()
    private val shuttingDown = AtomicBoolean()
    private val threadLock = ReentrantLock()

    init {
        this.maxNumThreads = maxNumThreads
    }
    override fun execute(command: Runnable) {
        if (started.compareAndSet(false, true)) {
            for (thread in threads) {
                thread?.start()
            }
        }

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
            // try, finnally를 newThread에만 걸어도 되는건지, needsMoreThreads까지 포함해야하는건지 궁금.
            try {
                if (needsMoreThreads()) {
                    newThread()
                }
            } finally {
                threadLock.unlock()
            }

        }
    }

    private fun newThread() {
        numActiveThreads.incrementAndGet()
        val newThread = Thread {
            try {
                var isActive = true
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
                            task = queue.take()
                            isActive = true
                            numActiveThreads.incrementAndGet()
                        }

                        if (task == SHUTDOWN_TASK) {
                            break
                        } else {
                            task.run()
                        }
                    } catch (t: Throwable) {
                        if (t !is InterruptedException) {
                            println("unexpected exception thrown")
                            t.printStackTrace()
                        }
                    }

                }
                println("shutting down - ${Thread.currentThread().name}")
            } finally {
                numActiveThreads.decrementAndGet()
            }
        }
        newThread.start()
        threads.add(newThread)
    }

    private fun needsMoreThreads(): Boolean {
        val numActiveThreads = this.numActiveThreads.get();
        return numActiveThreads < maxNumThreads && numActiveThreads >= threads.size
    }

    fun shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            for (thread in threads) {
                queue.add(SHUTDOWN_TASK)
            }
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