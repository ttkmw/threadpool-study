import java.util.concurrent.Executor
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration

class ThreadPool7(private val minNumWorkers: Int, private val maxNumWorkers: Int, idleTimeout: Duration) : Executor {
    private val workers = HashSet<Worker>()
    private val numWorkers = AtomicInteger()
    private val numBusyWorkers = AtomicInteger()
    private val queue = LinkedTransferQueue<Runnable>()
    private val started = AtomicBoolean()
    private val shutdown = AtomicBoolean()
    private val workerLock = ReentrantLock()
    private val idleTimeoutNanos = idleTimeout.inWholeNanoseconds

    companion object {
        private val SHUTDOWN_TASK = Runnable { }
    }

    private fun newWorker(workerType: WorkerType): Worker {
        numWorkers.incrementAndGet()
        numBusyWorkers.incrementAndGet()
        val newWorker = Worker(workerType)

        // 이희승님은 여기서 lock 안걸었는데... 왜 lock 안거신지 모르겠음 락 걸어야할것같은데.
        workerLock.lock()
        try {
            workers.add(newWorker)
        } finally {
            workerLock.unlock()
        }
        return newWorker
    }

    override fun execute(task: Runnable) {
        if (started.compareAndSet(false, true)) {
            for (worker in workers) {
                worker.start()
            }
        }

        if (shutdown.get()) {
            throw RejectedExecutionException()
        }

        queue.add(task)
        // queue.add보다 먼저 호출하면 무슨 문제가 생겼는지 까먹음.
        addWorkersIfNecessary()

        if (shutdown.get()) {
            queue.remove(task)
            throw RejectedExecutionException()
        }
    }

    private fun addWorkersIfNecessary() {
        if (needsMoreWorker() != null) {
            workerLock.lock()
            var newWorkers: MutableList<Worker>? = null
            try {
                while (!shutdown.get()) {
                    val workerType = needsMoreWorker() ?: break
                    if (newWorkers == null) {
                        newWorkers = mutableListOf()
                    }

                    newWorkers.add(newWorker(workerType))
                }

            } finally {
                workerLock.unlock()
            }

            newWorkers?.forEach { it.start() }
        }
    }

    private fun needsMoreWorker(): WorkerType? {
        val numWorkers = numWorkers.get()
        val numBusyWorkers = numBusyWorkers.get()
        if (numWorkers < minNumWorkers) {
            return WorkerType.CORE
        }
        if (numBusyWorkers >= numWorkers && numWorkers < maxNumWorkers) {
            return WorkerType.EXTRA
        }
        return null
    }

    fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            for (worker in workers) {
                queue.add(SHUTDOWN_TASK)
            }
        }

        // 여기서 while true 하는 이유 생각 안남.

        while (true) {
            val workers: Array<Worker>
            workerLock.lock()
            try {
                workers = this.workers.toTypedArray()
            } finally {
                workerLock.unlock()
            }

            if (workers.isEmpty()) {
                break
            }

            for (worker in workers) {
                worker.join()
            }
        }
    }

    enum class WorkerType {
        CORE,
        EXTRA
    }

    inner class Worker(private val workerType: WorkerType) {
        private val thread = Thread(this::work)

        private fun work() {
            var isBusy = true
            var lastRuntimeNanos = System.nanoTime()
            try {
                while (true) {
                    try {
                        var task = queue.poll()
                        if (task == null) {
                            if (isBusy) {
                                isBusy = false
                                numBusyWorkers.decrementAndGet()
                            }

                            when (workerType) {
                                WorkerType.CORE -> {
                                    task = queue.take()
                                }
                                WorkerType.EXTRA -> {
                                    val waitTimeNanos = idleTimeoutNanos - (System.nanoTime() - lastRuntimeNanos)
                                    task = queue.poll(waitTimeNanos, TimeUnit.NANOSECONDS)
                                    if (waitTimeNanos <= 0 || task == null) {
                                        println("${Thread.currentThread().name} is timed out")
                                        break
                                    }
                                }
                            }

                            isBusy = true
                            numBusyWorkers.incrementAndGet()
                        } else {
                            if (!isBusy) {
                                isBusy = true
                                numBusyWorkers.incrementAndGet()
                            }
                        }

                        if (task == SHUTDOWN_TASK) {
                            println("${Thread.currentThread().name} is received poison kill")
                            break
                        } else {
                            task.run()
                            lastRuntimeNanos = System.nanoTime()
                        }

                    } catch (t: Throwable) {
                        if (t !is InterruptedException) {
                            println("unexpected error occurred")
                            t.printStackTrace()
                        }
                    }
                }
            } finally {
                workerLock.lock()
                try {
                    workers.remove(this)
                    // 얘네 이 락 안에서 해야하는거였는지 까먹음.
                    numWorkers.decrementAndGet()
                    if (isBusy) {
                        numBusyWorkers.decrementAndGet()
                    }

                    if (workers.isEmpty() && queue.isNotEmpty()) {
                        for (task in queue) {
                            if (task != SHUTDOWN_TASK) {
                                addWorkersIfNecessary()
                                break
                            }
                        }
                    }
                } finally {
                    workerLock.unlock()
                }

                println("${Thread.currentThread().name} ($workerType) is shutting down")
            }
        }

        fun start() {
            thread.start()
        }

        fun join() {
            do {
                try {
                    thread.join()
                } catch (_: InterruptedException) {
                }
                //여기서 while을 붙인건 interrupt될까봐 그런거였나?
            } while (thread.isAlive)
        }
    }
}