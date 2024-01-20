package draft.concurrencystudy

class UnsafeCounter {
    // A single shared counter variable
    var count = 0

    fun increment() {
        count++ // increment the shared count
    }
}

fun main() {
    val unsafeCounter = UnsafeCounter()

    // Create 100 threads that increment the counter
    val threads = List(100) {
        Thread {
            unsafeCounter.increment()
        }
    }

    threads.forEach { it.start() }
    threads.forEach { it.join() } // Wait for all threads to complete

    println("Expected 100, Actual: ${unsafeCounter.count}")
}