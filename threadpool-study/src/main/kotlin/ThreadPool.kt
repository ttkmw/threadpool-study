import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/*
* 문제점
* 스레드 갯수를 정하는게 정적이다.
* */
class ThreadPool(numThreads: Int): Executor {

    private val SHUTDOWN_TASK = Runnable {  }
    private var threads: Array<Thread?>
    private val queue = LinkedTransferQueue<Runnable>()
    private val started = AtomicBoolean()
    private val shuttingDown = AtomicBoolean()

    init {
        threads = arrayOfNulls(numThreads)
        for (i in 0 ..< numThreads) {
            threads[i] = Thread {
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
                            println("unexpected exception thrown")
                            t.printStackTrace()
                        }
                    }

                }
                println("shutting down - ${Thread.currentThread().name}")
            }
        }
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

        if (shuttingDown.get()) {
            queue.remove(command)
            throw RejectedExecutionException()
        }
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