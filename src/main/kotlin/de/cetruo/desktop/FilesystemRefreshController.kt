package de.cetruo.desktop

import javafx.application.Platform
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class FilesystemRefreshController(
    private val scanner: CollectionImageScanner,
    private val onResult: (Path, CollectionScanResult) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cetruo-watch-scan").apply { isDaemon = true }
    }
    private val scheduled = AtomicBoolean(false)
    private val generation = AtomicLong()

    fun schedule(root: Path) {
        if (!scheduled.compareAndSet(false, true)) return
        val token = generation.get()

        executor.submit {
            val result = runCatching { scanner.scan(root) }.getOrNull()
            Platform.runLater {
                scheduled.set(false)
                if (generation.get() != token || result == null) return@runLater
                onResult(root, result)
            }
        }
    }

    fun invalidate() {
        generation.incrementAndGet()
    }
}
