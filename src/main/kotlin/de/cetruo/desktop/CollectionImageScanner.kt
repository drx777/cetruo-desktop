package de.cetruo.desktop

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

data class CollectionScanResult(
    val images: List<Path>,
    val nestedCollections: List<Path>
)

class CollectionImageScanner(
    private val supportedExtensions: Set<String> = setOf("jpg", "jpeg", "png", "gif")
) {
    fun scan(root: Path): CollectionScanResult {
        val found = mutableListOf<Path>()
        val nested = mutableSetOf<Path>()

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (Thread.currentThread().isInterrupted) return FileVisitResult.TERMINATE
                val name = dir.fileName?.toString()?.lowercase(Locale.ROOT).orEmpty()

                if (
                    dir != root &&
                    (
                        name == "cetruo desktop exports" ||
                        name == "card forge exports" ||
                        name == ".cetruo" ||
                        name == ".cetruo-exports" ||
                        name == ".cardforge" ||
                        name == ".cardforge-exports" ||
                        (
                            name == "exports" &&
                            (
                                dir.parent?.fileName?.toString() == ".cetruo" ||
                                dir.parent?.fileName?.toString() == ".cardforge"
                            )
                        )
                    )
                ) {
                    return FileVisitResult.SKIP_SUBTREE
                }

                if (dir != root && CollectionDatabase.hasCatalog(dir)) {
                    nested.add(dir.toAbsolutePath().normalize())
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (Thread.currentThread().isInterrupted) return FileVisitResult.TERMINATE
                if (attrs.isRegularFile && supports(file) && !isGeneratedExportFile(file)) {
                    found.add(file.toAbsolutePath().normalize())
                }
                return FileVisitResult.CONTINUE
            }
        })

        found.sortBy { root.relativize(it).toString().lowercase() }
        return CollectionScanResult(
            images = found,
            nestedCollections = nested.sortedBy { root.relativize(it).toString().lowercase() }
        )
    }

    fun supports(path: Path): Boolean {
        val extension = path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in supportedExtensions
    }

    private fun isGeneratedExportFile(path: Path): Boolean {
        val name = path.fileName.toString().lowercase(Locale.ROOT)
        return name.endsWith(".card.png") ||
            name.endsWith(".card.svg") ||
            name.endsWith("-card.png") ||
            name.endsWith("-card.svg") ||
            name.contains("cetruo-export") ||
            name.contains("card-forge-export")
    }
}
