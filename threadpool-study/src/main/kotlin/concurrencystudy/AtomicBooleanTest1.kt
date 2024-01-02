package concurrencystudy

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

var isCompleted2 = AtomicBoolean(false) // Shared AtomicBoolean resource

fun main() {
    // Starting 100 threads trying to modify isCompleted atomically
    for (i in 1..100) {
        thread(start = true) {
            if (isCompleted2.compareAndSet(false, true)) { // Atomically sets to true if current value is false
                println("Completed by thread $i")
            }
        }
    }
}