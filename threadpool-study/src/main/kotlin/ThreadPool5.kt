import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class ThreadPool5(private val maxNumThreads: Int): Executor {
    private val threads = HashSet<Thread>(maxNumThreads)
    val numThreads = AtomicInteger()
    val numActiveThreads = AtomicInteger()
    private val started = AtomicBoolean()
    private val shutdown = AtomicBoolean()
    val queue = LinkedTransferQueue<Runnable>()
    private val SHUTDOWN_TASK = Runnable {}
    private val threadLock = ReentrantLock()

    private fun newThread(): Thread {
        numThreads.incrementAndGet()
        numActiveThreads.incrementAndGet()

        val newThread = Thread {
            try {
                while (true) {
                    try {
                        var task = queue.poll()
                        if (task == null) {
                            numActiveThreads.decrementAndGet()

                            task = queue.take()
                            numActiveThreads.incrementAndGet()
                        }
                        if (task == SHUTDOWN_TASK) {
                            break
                        } else {
                            task.run()
                        }
                    } catch (t: Throwable) {
                        if (t !is InterruptedException) {
                            println("unexpected error")
                            t.printStackTrace()
                        }
                    }
                }

            } finally {
                println("Shutting down thread: ${Thread.currentThread().name}")
                // 여기서 왜 lock을 거는지 모름.
                threadLock.lock()
                try {
                    threads.remove(Thread.currentThread())
                } finally {
                    threadLock.unlock()
                }
                numThreads.decrementAndGet()
                numActiveThreads.decrementAndGet()
            }
        }
        threads.add(newThread)
        return newThread
    }

    override fun execute(task: Runnable) {
        if (shutdown.get()) {
            throw RejectedExecutionException()
        }
        queue.add(task)
        addThreadIfNecessary()

        if (shutdown.get()) {
            queue.remove(task)
            throw RejectedExecutionException()
        }
    }

    private fun addThreadIfNecessary() {
        if (needsMoreThread()) {
            // 락이 어떤식으로 걸리는건지 모름
            threadLock.lock()
            var newThread: Thread? = null
            try {
                if (needsMoreThread()) {
                    newThread = newThread()
                }
            } finally {
                threadLock.unlock()
            }

            //스타트를 왜 newThread 밖에서 하는지 모름. 게다가 일부러 threadLock 밖에서 한것같은데... 이것도 이유를 모르겠음.
            // lock 밖에서 호출한건데.
            // 이유: lock window를 최소화하기 위함이라고 함.
            // 이유: 다른 표현으로, 경쟁을 최소화하기 위해서 Lock window 밖에서 start한 것.
            // lock window가 그럼 뭐야?
            newThread?.start()
        }
    }

    private fun needsMoreThread(): Boolean {
        val numThreads = numThreads.get()
        return numThreads < maxNumThreads && numActiveThreads.get() >= numThreads
    }

    fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            //여기에 threads를 순회하는것과 maxNumThreads를 순회하는것의 차이를 모름
            for (i in 0 ..< maxNumThreads) {
                queue.add(SHUTDOWN_TASK)
            }
        }


        while (true) {
            threadLock.lock()
            val threads: Array<Thread>

            try {
                threads = this.threads.toTypedArray()
            } finally {
                threadLock.unlock()
            }

            if (threads.isEmpty()) {
                break
            }


            for (thread in threads) {
                do {
                    try {
                        thread.join()
                    } catch (_: InterruptedException){}
                } while (thread.isAlive)
            }
        }
    }
}