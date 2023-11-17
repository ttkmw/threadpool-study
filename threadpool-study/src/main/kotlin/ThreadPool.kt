import java.lang.RuntimeException
import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicBoolean

/*
* 문제점
* 1. shutdown 기능이 없다
* 2. 스레드 갯수를 정하는게 정적이다.
* 3. 큐에 태스크가 남아있어도 스레드풀이 끝나버린다.
* */
class ThreadPool(numThreads: Int): Executor {

    private var threads: Array<Thread?>
    private val queue = LinkedTransferQueue<Runnable>()
    private val started = AtomicBoolean()
    private var shuttingDown = false

    init {
        threads = arrayOfNulls(numThreads)
        for (i in 0 ..< numThreads) {
            threads[i] = Thread {
                // race condition 이슈
                while (!shuttingDown || queue.isNotEmpty()) {
                    var task: Runnable? = null
                    try {
                        task = queue.take()
                    } catch (_: InterruptedException) {
                    }

                    try {
                        task?.run()
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
        queue.add(command)
    }

    fun shutdown() {
        shuttingDown = true
        for (thread in threads) {
            thread?.interrupt()
        }

        for (thread in threads) {
            if (thread == null) {
                continue
            }
            while (true) {
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