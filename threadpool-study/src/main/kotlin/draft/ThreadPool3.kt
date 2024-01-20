package draft

import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

// 문제
// 1. shutdown을 하지 않으면, 한번 쓰인 스레드가 오랫동안 놀아도 계속 살아있는다.

// 1. AtomicBoolean을 쓰는 이유를 대강은 알지만 정확히 모른다.
// 2. queue.take와 run할 때 catch 하는 이유를 대강은 알지만 정확히 모른다.
// 3. 2때문에, shutdown task로 바꾼 다음에 catch를 여전히 남겨둬야 하는지 모른다.
// 4. 1 때문에, numActiveThreads를 AtomicInteger로 해야하는지, Integer로 해야하는지 확신이 없다.
// 5. threadLock을 하는게 어느 범위까지 락이 걸리는지 모른다.
// 6. 5 때문에, needsMoreThread() 하고 락걸고 다시 needsMoreThread() 할 때 false가 나오는 케이스가 있을지 확신하지 못했다.
// 7. newThread하고 그 안에서 thread를 start해도 되는지 확신하지 못했다. 이거 뭐라고 했었는데...
class ThreadPool3(private val maxNumThreads: Int): Executor{
    private val numActiveThreads = AtomicInteger()
    private val threads = HashSet<Thread>()
    private val queue = LinkedTransferQueue<Runnable>()
    private val started = AtomicBoolean()
    private val shutdown = AtomicBoolean()
    private val SHUTDOWN_TASK = Runnable { }
    private val threadLock = ReentrantLock()

    private fun newThread(): Thread {
        numActiveThreads.incrementAndGet()
        val newThread = Thread {
            try {
                while (true) {
                    try {
                        val task = queue.take()
                        if (task == SHUTDOWN_TASK) {
                            break
                        } else {
                            task.run()
                        }
                    } catch (t: Throwable) {
                        if (t !is InterruptedException) {
                            println("unexpected exception:")
                            t.printStackTrace()
                        }
                    }
                }
            } finally {
                println("shutting down - ${Thread.currentThread().name}")
                numActiveThreads.decrementAndGet()
            }

        }
        newThread.start()
        return newThread
    }

    override fun execute(task: Runnable) {
        if (started.compareAndSet(false, true)) {
            for (thread in threads) {
                thread.start()
            }
        }

        if (shutdown.get()) {
            throw RejectedExecutionException()
        }

        addThreadIfNecessary()

        queue.add(task)

        if (shutdown.get()) {
            queue.remove(task)
            throw RejectedExecutionException()
        }
    }

    private fun addThreadIfNecessary() {
        if (needsMoreThread()) {
            try {
                threadLock.lock()
                if (needsMoreThread()) {
                    val newThread = newThread()
                    threads.add(newThread)
                }
            } finally {
                threadLock.unlock()
            }
        }
    }

    private fun needsMoreThread() = threads.size < maxNumThreads && numActiveThreads.get() >= threads.size

    fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            for (thread in threads) {
                queue.add(SHUTDOWN_TASK)
            }
        }

        for (thread in threads) {
            do {
                try {
                    thread.join()
                } catch (_: InterruptedException) {}
            } while (thread.isAlive)
        }
    }
}