import draft.concurrencystudy.ReentrantLockStudy
import org.junit.jupiter.api.Test

class ReentrantLockStudyTest {

    @Test
    fun exampleMethod() {
        val reentrantLockStudy = ReentrantLockStudy()
        val thread1 = Thread { reentrantLockStudy.exampleMethod() }
        val thread2 = Thread { reentrantLockStudy.exampleMethod() }

        thread1.start()
        println("thread1 started")
        thread2.start()
        println("thread2 started")
    }
}