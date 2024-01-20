package draft

import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicBoolean

class ThreadPool4(numThreads: Int) : Executor {
    private val threads = arrayOfNulls<Thread>(numThreads)
    private val queue = LinkedTransferQueue<Runnable>()
    private val started = AtomicBoolean()
    private val shutdown = AtomicBoolean()

    init {
        for (i in 0..<numThreads) {
            threads[i] = Thread {
                while (!shutdown.get() || queue.isNotEmpty()) {
                    try {
                        val task = queue.take()
                        task.run()
                    } catch (t: Throwable) {
                        if (t !is InterruptedException) {
                            println("unexpected exception occurred")
                            t.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    override fun execute(task: Runnable) {
        if (started.compareAndSet(false, true)) {
            for (thread in threads) {
                thread?.start()
            }
        }
        queue.add(task)
    }

    fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            for (thread in threads) {
                thread?.interrupt()
            }
        }

        for (thread in threads) {
            while (true) {
                if (thread == null) {
                    continue
                }
                try {
                    thread.join()
                } catch (_: InterruptedException) {
                }

                if (!thread.isAlive) {
                    break
                }

                thread.interrupt()
            }
        }

    }
}