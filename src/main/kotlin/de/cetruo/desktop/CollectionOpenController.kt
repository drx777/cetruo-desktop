package de.cetruo.desktop

import javafx.concurrent.Task
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

data class OpenedCollection(
    val root: Path,
    val scan: CollectionScanResult,
    val database: CollectionDatabase
)

class CollectionOpenController(
    private val scanner: CollectionImageScanner,
    private val onScanning: (Path) -> Unit,
    private val onOpened: (OpenedCollection) -> Unit,
    private val onError: (String, Throwable) -> Unit
) {
    private val generation = AtomicLong()
    private var task: Task<CollectionScanResult>? = null

    fun open(directory: Path) {
        val normalized = directory.toAbsolutePath().normalize()
        cancel()
        val token = generation.incrementAndGet()
        onScanning(normalized)

        val nextTask = object : Task<CollectionScanResult>() {
            override fun call(): CollectionScanResult = scanner.scan(normalized)
        }
        task = nextTask

        nextTask.setOnSucceeded {
            if (generation.get() != token) return@setOnSucceeded
            try {
                val database = CollectionDatabase.open(normalized)
                if (generation.get() != token) {
                    database.close()
                    return@setOnSucceeded
                }
                onOpened(OpenedCollection(normalized, nextTask.value, database))
            } catch (e: Exception) {
                if (generation.get() == token) onError("Could not open collection", e)
            }
        }
        nextTask.setOnFailed {
            if (generation.get() == token) {
                onError(
                    "Could not scan collection",
                    nextTask.exception ?: RuntimeException("Unknown scanning error")
                )
            }
        }

        Thread(nextTask, "cetruo-scan").apply { isDaemon = true }.start()
    }

    fun cancel() {
        generation.incrementAndGet()
        task?.cancel()
        task = null
    }
}
