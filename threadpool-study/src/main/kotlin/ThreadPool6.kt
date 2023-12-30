import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration

class ThreadPool6(private val maxNumThreads: Int, idleTimeout: Duration): Executor {
    private val threads = HashSet<Thread>()
    private val numThreads = AtomicInteger()
    private val numBusyThreads = AtomicInteger()
    private val queue = LinkedTransferQueue<Runnable>()
    private val shutdown = AtomicBoolean()
    private val threadLock = ReentrantLock()
    private val idleTimeoutNanos = idleTimeout.inWholeNanoseconds
    companion object {
        val SHUTDOWN_TASK = Runnable {}
    }

    private fun newThread(): Thread {
        numThreads.incrementAndGet()
        numBusyThreads.incrementAndGet()
        val newThread =  Thread {
            println("Start a new thread: ${Thread.currentThread().name}")
            var isBusy = true
            var lastRuntimeNanos = System.nanoTime()
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
                            val waitTimeoutNanos = idleTimeoutNanos - (System.nanoTime() - lastRuntimeNanos)
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
                                lastRuntimeNanos = System.nanoTime()
                            }
                        }
                    } catch (t: Throwable) {
                        if (t !is InterruptedException) {
                            println("unexpected error occurred")
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
            threadLock.lock()
            var newThread: Thread? = null
            try {
                //4. shutdown은 왜 체크하는거였지?
                if (needsMoreThread() && !shutdown.get()) {
                    newThread = newThread()
                }
            } finally {
                threadLock.unlock()
            }
            newThread?.start()
        }
    }

    private fun needsMoreThread(): Boolean {
        val numBusyThreads = numBusyThreads.get()
        val numThreads = numThreads.get()
        return numBusyThreads >= numThreads && numThreads < maxNumThreads
    }

    fun shutdown() {
        // 2. 주기적으로 interrupt를 해야하는 이유는 알 것 같다. thread.run할 때 interrupt되면, 이후 interrupt가 안됨.
        // 3. 그럼 질문, 주기적으로 interrupt를 하더라도, run할 때 sleep을 뒀고 거기서 interrupt되면 sleep 밑의 코드들은 동작을 안할텐데. 예상치 못한 동작 아닌가? 쓰는 사람은 shutdown을 했더라도 큐에 있는 task는 온전히 끝날걸 예상할것 같은데.
        // 예를들어, 스레드풀(P1)가 execute하여 하나의 스레드(C1)가 하나의 태스크A를 run 했고, 태스크A에는 @Transactional이 붙어있어서 원자적이길 기대되고 있다. 태스크A는 먼저 쿼리A를 데이터베이스에 날리고 Thread.sleep해서 100초간 멈춘 후 쿼리B를 날리는 코드로 되어있다. 그때 P1이 shutdown을 호출해서 C1이 wakeup 했다. C1은 쿼리A를 쏘는 코드는 호출하고 쿼리B를 쏘는 코드는 호출하지 않았는데,
        // C1은 InterruptedException을 무시했고 그래서 롤백되지 않았다.
        if (shutdown.compareAndSet(false, true)) {
            for (thread in threads) {
                queue.add(SHUTDOWN_TASK)
            }
        }
        while (true) {
            val threads: Array<Thread>
            threadLock.lock()
            try {
                threads = this.threads.toTypedArray()
            } finally {
                threadLock.unlock()
            }

            if (threads.isEmpty()) {
                println("broke")
                break
            }

            for (thread in threads) {
//                질문1 왜 여기서 while true를 쓰는걸까? 그냥 한번만 join 해도 될 것 같은데... interrupt 호출될수도 있어서 그런가? 누가 interrupt할 수 있는거지?
                do {
                    try {
                        thread.join()
                    } catch (_: InterruptedException) {}
                } while (thread.isAlive)
            }
        }
    }
}