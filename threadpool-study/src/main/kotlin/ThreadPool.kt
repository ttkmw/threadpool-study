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
        val newThread = Thread {
            try {
                while (true) {
                    try {
                        val task = queue.take()
                        numActiveThreads.incrementAndGet()
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