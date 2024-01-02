package concurrencystudy

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class Counter {
    // Shared atomic boolean among threads (thread-safe)
    var isActive = AtomicBoolean(false)

    fun activate() {
        // Simulate some work by sleeping
        Thread.sleep(100)
        isActive.set(true)
    }

    fun deactivate() {
        isActive.set(false)
    }
}

fun main() {
    val counter = Counter()

    val thread1 = thread {
        counter.activate()
        println("Thread1: isActive = ${counter.isActive.get()}")
    }

    val thread2 = thread {
        // Wait for a moment before deactivating
        Thread.sleep(50)
        counter.deactivate()
        println("Thread2: isActive = ${counter.isActive.get()}")
    }

    // Try to read isActive here while threads are likely still running
    println("During Threads: isActive = ${counter.isActive.get()}")

    thread1.join()
    thread2.join()

    // Final state of isActive after all threads are complete
    println("After Threads: isActive = ${counter.isActive.get()}")
}