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

    init {
        threads = arrayOfNulls(numThreads)
        for (i in 0 ..< numThreads) {
            threads[i] = Thread {
                while (true) {
                    try {
                        val task = queue.take()
                        task.run()
                    } catch (e: InterruptedException) {
                        println("threadPool will be shutdown")
                        throw RuntimeException()
                    }
                }
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
        for (thread in threads) {
            thread?.interrupt()
        }
    }

}