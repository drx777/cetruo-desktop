package com.example.cardforge

import javafx.application.Platform
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Lightweight elapsed-time logging and JavaFX stall diagnostics for collection startup/open. */
object StartupProfiler {
    private val starts = ConcurrentHashMap<String, Long>()
    private val stallMonitorInstalled = AtomicBoolean(false)
    private val fxThread = AtomicReference<Thread?>()
    private val probePending = AtomicBoolean(false)
    private val probeScheduledAt = AtomicLong(0L)
    private val lastReportedProbe = AtomicLong(0L)

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

    /** Installs a low-overhead watchdog that reports JavaFX event-thread stalls. */
    fun installFxStallMonitor(warningAfterMs: Long = 700, pollEveryMs: Long = 200) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater { installFxStallMonitor(warningAfterMs, pollEveryMs) }
            return
        }
        if (!stallMonitorInstalled.compareAndSet(false, true)) return

        fxThread.set(Thread.currentThread())
        log("FX stall monitor installed · warning=${warningAfterMs}ms")

        val executor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "card-forge-startup-profiler").apply { isDaemon = true }
        }
        executor.scheduleAtFixedRate({
            try {
                val now = System.nanoTime()
                if (probePending.compareAndSet(false, true)) {
                    probeScheduledAt.set(now)
                    Platform.runLater {
                        val delayMs = (System.nanoTime() - probeScheduledAt.get()) / 1_000_000.0
                        if (delayMs >= warningAfterMs) {
                            log("FX responsive again · queued probe delayed %.1f ms".format(delayMs))
                        }
                        probePending.set(false)
                    }
                } else {
                    val scheduledAt = probeScheduledAt.get()
                    val blockedMs = (now - scheduledAt) / 1_000_000
                    if (blockedMs >= warningAfterMs && lastReportedProbe.getAndSet(scheduledAt) != scheduledAt) {
                        val thread = fxThread.get()
                        val stack = thread?.stackTrace
                            ?.take(18)
                            ?.joinToString("\n") { "    at $it" }
                            .orEmpty()
                        log(buildString {
                            append("FX STALL · ")
                            append(blockedMs)
                            append(" ms")
                            if (stack.isNotBlank()) {
                                append("\n")
                                append(stack)
                            }
                        })
                    }
                }
            } catch (t: Throwable) {
                log("stall monitor error: ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
            }
        }, pollEveryMs, pollEveryMs, TimeUnit.MILLISECONDS)
    }

    private fun log(message: String) {
        val thread = Thread.currentThread().name
        System.err.println("[startup] $message · thread=$thread")
    }
}
