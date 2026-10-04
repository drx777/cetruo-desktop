package com.example.cardforge

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.prefs.Preferences

/** Stores the five most recently opened collection roots for the startup chooser. */
object RecentCatalogs {
    private const val KEY = "recentCatalogs"
    private const val LIMIT = 5
    private val prefs: Preferences = Preferences.userNodeForPackage(RecentCatalogs::class.java)

    fun list(): List<Path> = prefs.get(KEY, "")
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
        prefs.put(KEY, paths.joinToString("\n") { it.toString() })
    }

    fun forget(root: Path) {
        val normalized = root.toAbsolutePath().normalize()
        prefs.put(KEY, list().filterNot { it == normalized }.joinToString("\n") { it.toString() })
    }
}
