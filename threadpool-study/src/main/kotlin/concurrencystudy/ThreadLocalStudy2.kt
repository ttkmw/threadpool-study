package concurrencystudy

class SafeCounter {
    // ThreadLocal integer for the count
    private val threadLocalCount = ThreadLocal.withInitial { 0 }

    fun increment() {
        val currentCount = threadLocalCount.get() // Get the current count for the thread
        threadLocalCount.set(currentCount + 1) // Increment the count for the thread
    }

    fun getThreadCount(): Int = threadLocalCount.get()
}

fun main() {
    val safeCounter = SafeCounter()

    // Create 100 threads that increment their own counter and print it
    val threads = List(100) {
        Thread {
            safeCounter.increment()
            // Print each thread's count after incrementing
            println("Thread ${Thread.currentThread().id} count: ${safeCounter.getThreadCount()}")
        }
    }

    threads.forEach { it.start() }
    threads.forEach { it.join() } // Wait for all threads to complete
}