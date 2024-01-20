package draft.concurrencystudy

import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    val example = BooleanIssueExample()
    val thread = Thread(example)
    thread.start()

    Thread.sleep(100) // Simulate some other operations
    example.stopRunning() // This might not be immediately visible to the thread
}
class BooleanIssueExample : Runnable {
    private val shouldRun = AtomicBoolean(true)

    fun stopRunning() {
        shouldRun.set(false)
    }

    override fun run() {
        while (shouldRun.get()) {
            for (i in 0 .. 10) {
                println("running $i")
                if (!shouldRun.get()) {
                    break
                }
            }
        }
        println("Thread finished")
    }
}