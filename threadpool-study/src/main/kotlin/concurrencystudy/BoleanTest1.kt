package concurrencystudy

import kotlin.concurrent.thread

var isCompleted1 = false // Shared Boolean resource

fun main() {
    // Starting 100 threads trying to modify isCompleted
    for (i in 1..100) {
        thread(start = true) {
            if (!isCompleted1) {
                // Simulating some work
                Thread.sleep(10)
                isCompleted1 = true
                println("Completed by thread $i")
            }
        }
    }
}