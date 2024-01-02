package concurrencystudy
import java.util.concurrent.locks.ReentrantLock

fun main() {
    // Create a HashSet which will be accessed by multiple threads
    val hashSet: MutableSet<Int> = HashSet()
    val lock = ReentrantLock()


    // Thread 1: Adds elements to the HashSet
    val thread1 = Thread {
        for (i in 0..999) {
            lock.lock();  // Acquire the lock
            try {
                hashSet.add(i);
            } finally {
                lock.unlock();  // Release the lock
            }
        }
    }


    // Thread 2: Removes elements from the HashSet
    val thread2 = Thread {
        for (i in 0..999) {
            lock.lock();  // Acquire the lock
            try {
                hashSet.remove(i);
            } finally {
                lock.unlock();  // Release the lock
            }
        }
    }


    // Start both threads
    thread1.start()
    thread2.start()


    // Wait for both threads to finish
    thread1.join()
    thread2.join()


    // Print the HashSet size
    println("HashSet size: " + hashSet.size)

}