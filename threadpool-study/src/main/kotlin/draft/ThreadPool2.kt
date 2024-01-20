package draft

import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

//ThreadPool과 똑같다. 단지 한번 더 타이핑해보면서 연습한 것이다.
class ThreadPool2(private val numThreads: Int) : Executor {
    private val threads = arrayOfNulls<Thread>(numThreads)
    private val queue = LinkedTransferQueue<Runnable>()
    private val started = AtomicBoolean()
    private val shuttingDown = AtomicBoolean()
    private val SHUTDOWN_TASK = Runnable {}

    init {
        for (i in 0..<numThreads) {
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
                            println("unexpected error: ")
                            t.printStackTrace()
                        }

                    }
                }

                println("shutting down - ${Thread.currentThread().name}")
            }
        }

    }

    override fun execute(task: Runnable) {
        if (started.compareAndSet(false, true)) {
            for (thread in threads) {
                thread?.start()
            }
        }

        if (shuttingDown.get()) {
            throw RejectedExecutionException()
        }
        queue.add(task)

        if (shuttingDown.get()) {
            queue.remove(task)
            throw RejectedExecutionException()

        }
    }

    fun shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            for (i in 0..<numThreads) {
                queue.add(SHUTDOWN_TASK)
            }

            for (i in 0..<numThreads) {
                threads[i]?.interrupt()
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