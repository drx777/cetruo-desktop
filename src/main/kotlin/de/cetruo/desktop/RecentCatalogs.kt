package de.cetruo.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Stores the five most recently opened collection roots for the startup chooser. */
object RecentCatalogs {
    private const val KEY = "recentCatalogs"
    private const val LIMIT = 5
    fun list(): List<Path> = AppPreferences.get(KEY, "")
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map(Paths::get)
        .map { it.toAbsolutePath().normalize() }
        .filter { Files.isDirectory(it) }
        .distinct()
        .take(LIMIT)
        .toList()

    fun record(root: Path) {
        val normalized = root.toAbsolutePath().normalize()
        val paths = buildList {
            add(normalized)
            addAll(list().filterNot { it == normalized })
        }.take(LIMIT)
        AppPreferences.put(KEY, paths.joinToString("\n") { it.toString() })
    }

    fun forget(root: Path) {
        val normalized = root.toAbsolutePath().normalize()
        AppPreferences.put(KEY, list().filterNot { it == normalized }.joinToString("\n") { it.toString() })
    }
}
