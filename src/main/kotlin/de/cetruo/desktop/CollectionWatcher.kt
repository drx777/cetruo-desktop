package de.cetruo.desktop

import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes

class CollectionWatcher(
    private val onImageModified: (Path) -> Unit,
    private val onRefreshRequested: () -> Unit
) : AutoCloseable {
    private var watchService: WatchService? = null
    private var watchThread: Thread? = null

    fun start(root: Path) {
        stop()
        val service = FileSystems.getDefault().newWatchService()
        watchService = service
        val thread = Thread({
            runCatching { registerWatchTree(service, root) }
            while (!Thread.currentThread().isInterrupted) {
                val key = runCatching { service.take() }.getOrNull() ?: break
                var relevant = false
                for (event in key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        relevant = true
                        continue
                    }

                    val dir = key.watchable() as? Path ?: continue
                    val rel = event.context() as? Path ?: continue
                    val child = dir.resolve(rel).toAbsolutePath().normalize()

                    when (event.kind()) {
                        StandardWatchEventKinds.ENTRY_CREATE -> {
                            if (Files.isDirectory(child) && !CollectionDatabase.hasCatalog(child)) {
                                runCatching { registerWatchTree(service, child) }
                            }
                            relevant = true
                        }

                        StandardWatchEventKinds.ENTRY_DELETE -> relevant = true

                        StandardWatchEventKinds.ENTRY_MODIFY -> {
                            onImageModified(child)
                            if (
                                CollectionDatabase.isCatalogFileName(child.fileName.toString()) ||
                                child.fileName.toString().endsWith(".card.json", ignoreCase = true)
                            ) {
                                relevant = true
                            }
                        }
                    }
                }

                key.reset()
                if (relevant) onRefreshRequested()
            }
        }, "cetruo-filesystem-watcher")
        thread.isDaemon = true
        watchThread = thread
        thread.start()
    }

    private fun registerWatchTree(service: WatchService, root: Path) {
        if (!Files.isDirectory(root)) return
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != root && CollectionDatabase.hasCatalog(dir)) return FileVisitResult.SKIP_SUBTREE
                dir.register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                )
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun stop() {
        watchThread?.interrupt()
        watchThread = null
        runCatching { watchService?.close() }
        watchService = null
    }

    override fun close() = stop()
}
