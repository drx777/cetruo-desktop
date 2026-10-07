package de.cetruo.desktop

import java.nio.file.Path

/**
 * Deterministic identity reconciliation used by filesystem scan updates.
 *
 * Missing paths are detached before newly added paths are reconciled so moves/renames can
 * recover the original asset identity by content hash. Unrelated replacements do not match.
 */
object FileIdentityReconciler {
    data class Result(
        val removed: Set<Path>,
        val added: Set<Path>,
        val reattachedAssetIds: Map<Path, String>
    )

    fun reconcile(
        previousPaths: Collection<Path>,
        nextPaths: Collection<Path>,
        database: CollectionDatabase?
    ): Result {
        val previous = previousPaths.map { it.toAbsolutePath().normalize() }.toSet()
        val next = nextPaths.map { it.toAbsolutePath().normalize() }.toSet()
        val removed = previous - next
        val added = next - previous

        if (database == null) return Result(removed, added, emptyMap())

        // Order matters: detach missing assets first, then match new files by content hash.
        removed.forEach { path -> runCatching { database.markMissing(path) } }

        val reattached = linkedMapOf<Path, String>()
        added.forEach { path ->
            runCatching { database.reconcileAdded(path) }.getOrNull()
                ?.let { assetId -> reattached[path] = assetId }
        }
        return Result(removed, added, reattached)
    }
}
