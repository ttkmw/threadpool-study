package draft.concurrencystudy;

import java.util.HashSet;
import java.util.Set;

public class NonThreadSafeHashSetDemo2 {
    public static void main(String[] args) throws InterruptedException {
        // Create a HashSet which will be accessed by multiple threads
        Set<Integer> hashSet = new HashSet<>();

        // Thread 1: Adds elements to the HashSet
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                hashSet.add(i);
            }
        });

        // Thread 2: Removes elements from the HashSet
        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                hashSet.remove(i);
            }
        });

        // Start both threads
        thread1.start();
        thread2.start();

        // Wait for both threads to finish
        thread1.join();
        thread2.join();

        // Print the HashSet size
        System.out.println("HashSet size: " + hashSet.size());
        // Ideally, the size should be 0, but due to concurrent modifications, the result might be unpredictable
    }
}
