#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/kotlin/com/example/cardforge/Main.kt"

text = MAIN.read_text()

old = '''    private fun resolveCardDataForSelection(path: Path): CardData? {
        val db = database ?: return newCardDefaults(path)
        val dbData = db.dataSnapshotForPath(path)
        if (dbData != null && hasMeaningfulCardData(dbData)) return dbData
        return newCardDefaults(path).also { defaults ->
            dbData?.assetId?.takeIf { it.isNotBlank() }?.let { defaults.assetId = it }
        }
    }
'''
new = '''    private fun resolveCardDataForSelection(path: Path): CardData? {
        val db = database ?: return newCardDefaults(path)
        val dbData = db.dataSnapshotForPath(path)
        if (dbData != null && hasMeaningfulCardData(dbData)) {
            if (dbData.collectorNumber.isBlank()) {
                dbData.collectorNumber = nextUnusedCollectorNumber()
                db.save(path, dbData)
            }
            return dbData
        }
        return newCardDefaults(path).also { defaults ->
            dbData?.assetId?.takeIf { it.isNotBlank() }?.let { defaults.assetId = it }
        }
    }
'''
if old not in text:
    raise RuntimeError("resolveCardDataForSelection integration block not found")
text = text.replace(old, new, 1)

# activity.asset_id has a foreign-key constraint; a collection-level migration must not
# insert an activity row with an empty asset id.
old_activity = '        if (removed > 0) db.recordActivity("", "SIDECAR_MIGRATION", "imported=$imported removed=$removed")\n'
new_activity = '        if (removed > 0) statusBarLabel.text = "Migrated $imported legacy sidecars; removed $removed legacy files."\n'
if old_activity not in text:
    raise RuntimeError("sidecar migration activity block not found")
text = text.replace(old_activity, new_activity, 1)

MAIN.write_text(text)
print("Applied 0.14.4 integration edge-case fixes")
