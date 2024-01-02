package concurrencystudy

import java.util.concurrent.locks.ReentrantLock

class ReentrantLockStudy {
    private val lock: ReentrantLock = ReentrantLock()
    fun exampleMethod() {
        lock.lock()
        try {
            println("${Thread.currentThread().name} is running")
        } finally {
            lock.unlock()
        }
    }
}