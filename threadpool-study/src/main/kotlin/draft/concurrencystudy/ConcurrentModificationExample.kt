package draft.concurrencystudy

fun main() {

    // Create a HashSet that will be modified while iterating
    val numbers: MutableSet<Int> = HashSet()


    // Adding initial elements to the HashSet
    for (i in 0..9) {
        numbers.add(i)
    }


    // Thread 1: Iterates over the HashSet
    val iteratorThread = Thread {
        try {
            val it: Iterator<Int> = numbers.iterator()
            while (it.hasNext()) {
                val number = it.next()
                println("Read number: $number")
                Thread.sleep(50) // Simulate some processing
            }
        } catch (e: ConcurrentModificationException) {
            System.err.println("ConcurrentModificationException caught!")
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


    // Thread 2: Modifies the HashSet
    val modifierThread = Thread {
        try {
            for (i in 10..14) {
                println("Adding number: $i")
                numbers.add(i)
                Thread.sleep(50) // Simulate some processing
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


    // Start both threads
    iteratorThread.start()
    modifierThread.start()
}