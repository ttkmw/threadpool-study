package concurrencystudy

import java.util.concurrent.atomic.AtomicReference

class Context {

    companion object {
        private var value = Thread.currentThread().name

        fun update(string: String) {
            value += string
        }

        fun get(): String {
            return value
        }
    }
}

class Context2 {
    private var value: String = ""
    fun update(string: String) {
        value += string
        Thread.sleep(10)
    }

    fun get(): String {
        return value
    }
}

class A {
    fun a() {
        Context.update("a")
    }
}

class B {
    fun b() {
        Context.update("b")
    }
}

class C {
    fun c() {
        Context.update("c")
    }
}

fun main() {
    val context = Context2()
    val runnable = {
        context.update("a")
        context.update("b")
        context.update("c")
        println(context.get())
    }
    val thread1 = Thread(runnable)

    val thread2 = Thread(runnable)

    thread1.start()
    thread2.start()

    thread1.join()
    thread2.join()
}