package com.example.cardforge

import java.util.concurrent.ConcurrentHashMap

/** Lightweight elapsed-time logging for collection startup/open diagnostics. */
object StartupProfiler {
    private val starts = ConcurrentHashMap<String, Long>()

    fun begin(name: String) {
        starts[name] = System.nanoTime()
        log("BEGIN $name")
    }

    fun end(name: String, detail: String = "") {
        val started = starts.remove(name)
        val elapsedMs = started?.let { (System.nanoTime() - it) / 1_000_000.0 }
        val timing = elapsedMs?.let { "%.1f ms".format(it) } ?: "unknown"
        log("END   $name · $timing${if (detail.isBlank()) "" else " · $detail"}")
    }

    inline fun <T> measure(name: String, detail: (T) -> String = { "" }, block: () -> T): T {
        begin(name)
        return try {
            block().also { end(name, detail(it)) }
        } catch (t: Throwable) {
            end(name, "FAILED: ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
            throw t
        }
    }

    fun mark(message: String) = log(message)

    private fun log(message: String) {
        val thread = Thread.currentThread().name
        System.err.println("[startup] $message · thread=$thread")
    }
}
