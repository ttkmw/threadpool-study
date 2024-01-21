import com.google.common.base.Preconditions.checkState
import org.slf4j.LoggerFactory
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.log

/*
* 문제점
* 태스크를 실행할 때 스레드가 있다고 판단해서 스레드를 추가하지 않았다
* 태스크a를 큐에 넣었다
* 스레드를 종료했다.
* 태스크a가 실행되지 않는다. - 문제
*
* */
class ThreadPool(
    private val minNumWorkers: Int, private val maxNumWorkers: Int,
    private val idleTimeoutNanos: Long, private val queue: BlockingQueue<Runnable>,
    private val submissionHandler: TaskSubmissionHandler,
    private val exceptionHandler: TaskExceptionHandler,
) : Executor {


    companion object {
        private val SHUTDOWN_TASK = Runnable { }

        private val logger = LoggerFactory.getLogger(ThreadPool::class.java)

        fun of(maxNumWorkers: Int): ThreadPool {
            return builder(maxNumWorkers).build()
        }

        fun of(minNumWorkers: Int, maxNumWorkers: Int): ThreadPool {
            return builder(maxNumWorkers).minNumWorkers(minNumWorkers).build()
        }

        fun builder(maxNumWorkers: Int): ThreadPoolBuilder {
            return ThreadPoolBuilder(maxNumWorkers)
        }
    }


    private val numBusyWorkers = AtomicInteger()
    private val numWorkers = AtomicInteger()
    var workers = HashSet<Worker>()
    private val shuttingDown = AtomicBoolean()
    val workersLock = ReentrantLock()
    override fun execute(task: Runnable) {
        if (!handleLateSubmission(task)) {
            return
        }

        if (!handleSubmission(task)) return

        addWorkerIfNecessary()

        if (shuttingDown.get()) {
            this.queue.remove(task)
            val accepted = handleLateSubmission(task)
            assert(!accepted)
        }
    }

    private fun handleSubmission(task: Runnable): Boolean {
        val taskAction = submissionHandler.handleSubmission(task = task, threadPool = this)
        if (taskAction == TaskActions.ACCEPT) {
            this.queue.add(task)
            return true
        }
        taskAction.doAction(task)
        return false

    }

    private fun handleLateSubmission(task: Runnable): Boolean {
        if (!shuttingDown.get()) {
            return true
        }
        val taskAction = submissionHandler.handleLateSubmission(task, this)
        checkState(taskAction != TaskAction.accept(), "Task Action must not be Accept")
        taskAction.doAction(task)
        return false
    }

    inner class Worker(private val expirationMode: ExpirationMode) {
        private val thread: Thread

        init {
            thread = Thread(this::work)
        }

        fun start() {
            thread.start()
        }

        private fun work() {
            val threadName = Thread.currentThread().name
            logger.debug("Started an new thread: {}, (expiration mode: {})", threadName, expirationMode)

            var isBusy = true
            var lastRunTimeNanos = System.nanoTime()
            try {
                while (true) {
                    var task: Runnable? = null
                    try {
                        task = this@ThreadPool.queue.poll()
                        if (task != null) {
                            if (!isBusy) {
                                isBusy = true
                                numBusyWorkers.incrementAndGet()
                            }
                        } else {
                            if (isBusy) {
                                isBusy = false
                                numBusyWorkers.decrementAndGet()
                            }

                            when (expirationMode) {
                                ExpirationMode.NEVER -> {
                                    task = this@ThreadPool.queue.take()
                                }

                                ExpirationMode.ON_IDLE -> {
                                    val waitTimeoutNanos = idleTimeoutNanos - (System.nanoTime() - lastRunTimeNanos)
                                    if (waitTimeoutNanos <= 0) {
                                        logger.debug("Terminating an idle worker {}", "$threadName hit by idle timeout")
                                        break
                                    }

                                    task = this@ThreadPool.queue.poll(waitTimeoutNanos, TimeUnit.NANOSECONDS)
                                    if (task == null) {
                                        logger.debug("Terminating an idle worker {}", "$threadName hit by idle timeout")
                                        break
                                    }
                                }
                            }

                            isBusy = true
                            numBusyWorkers.incrementAndGet()
                        }

                        if (task == SHUTDOWN_TASK) {
                            logger.debug("Terminating worker with a posion pill {}", threadName)
                            break
                        } else {
                            try {
                                task.run()
                            } finally {
                                lastRunTimeNanos = System.nanoTime()
                            }
                        }
                    } catch (cause: Throwable) {
                        if (cause !is InterruptedException) {
                            if (task != null) {
                                try {
                                    exceptionHandler.handleTaskException(task = task, cause = cause, threadPool = this@ThreadPool)
                                } catch (t2: Throwable) {
                                    t2.addSuppressed(cause)
                                    logger.warn("unexpected error occurred from task exception handler:", t2)
                                }
                            } else {
                                logger.warn("unexpected exception:", cause)
                            }
                        }
                    }
                }
            } finally {
                workersLock.lock()
                try {
                    workers.remove(this)
                    numWorkers.decrementAndGet()
                    numBusyWorkers.decrementAndGet() // Was busy handling the 'SHUTDOWN_TASK'

                    if (workers.isEmpty() && this@ThreadPool.queue.isNotEmpty()) {
                        for (task in this@ThreadPool.queue) {
                            if (task != SHUTDOWN_TASK) {
                                // We found the situation when
                                // - there are no active threads available and
                                // - there are tasks in the queue
                                // Start a new thread so that it's picked up
                                addWorkerIfNecessary()
                                break
                            }
                        }
                    }
                } finally {
                    workersLock.unlock()
                }
                logger.debug("A worker has been terminated {}, (expiration mode: {})", threadName, expirationMode)
            }
        }

        fun join() {
            while (thread.isAlive) {
                try {
                    thread.join()
                } catch (_: InterruptedException) {

                }
            }
        }
    }

    private fun addWorkerIfNecessary() {
        if (needsMoreWorker() != null) {
            workersLock.lock()
            var newWorkers: MutableList<Worker>? = null
            // try, finally를 newThread에만 걸어도 되는건지, needsMoreThreads까지 포함해야하는건지 궁금.
            try {

                while (!shuttingDown.get()) {
                    val expirationMode = needsMoreWorker()
                    if (expirationMode != null) {
                        if (newWorkers == null) {
                            newWorkers = ArrayList()
                        }
                        newWorkers.add(newWorker(expirationMode))
                    } else {
                        break
                    }
                }
            } finally {
                workersLock.unlock()
            }

            newWorkers?.forEach(Worker::start)
        }
    }

    private fun newWorker(expirationMode: ExpirationMode): Worker {
        numWorkers.incrementAndGet()
        numBusyWorkers.incrementAndGet()
        val newWorker = Worker(expirationMode)
        workers.add(newWorker)
        return newWorker
    }

    enum class ExpirationMode {
        /**
         * the worker that never gets terminated
         */
        NEVER,

        /**
         * the worker that can be terminated due to idle timeout
         */
        ON_IDLE
    }

    /**
     * Returns the worker type if more worker is needed to handle newly submitted task.
     * {@code null} is returned if no worker is needed
     */
    private fun needsMoreWorker(): ExpirationMode? {
        val numBusyWorkers = this.numBusyWorkers.get();
        val numWorkers = numWorkers.get()
        // Needs more threads if there are too few threads; we need at least `minNumThreads` threads
        if (numWorkers < minNumWorkers) {
            return ExpirationMode.NEVER
        }
        // Needs more threads if all threads are busy
        if (numBusyWorkers >= numWorkers) {
            // But we shouldn't create more threads than `maxNumThreads`
            if (numBusyWorkers < maxNumWorkers) {
                return if (idleTimeoutNanos > 0) ExpirationMode.ON_IDLE else ExpirationMode.NEVER
            }
        }

        return null


    }

    fun shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            for (i in 0..<maxNumWorkers) {
                this.queue.add(SHUTDOWN_TASK)
            }
        }
        while (true) {
            val workers = arrayOfNulls<Worker>(this.workers.size)
            workersLock.lock()
            try {
                this.workers.toArray(workers)
            } finally {
                workersLock.unlock()
            }

            if (workers.isEmpty()) {
                break
            }


            for (worker in workers) {

                if (worker == null) {
                    continue
                }
                worker.join()
            }
        }
    }


}