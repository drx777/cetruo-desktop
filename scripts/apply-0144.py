#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/kotlin/com/example/cardforge/Main.kt"
RENDERER = ROOT / "src/main/kotlin/com/example/cardforge/CardRenderer.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

m = MAIN.read_text()

m = replace_once(m,
'''    private val imageMode = ComboBox<ImageMode>()
    private val statusChoice = ComboBox<CardStatus>()''',
'''    private val imageMode = ComboBox<ImageMode>()
    private val imageBleedOverFrame = CheckBox("Artwork bleeds over frame")
    private val statusChoice = ComboBox<CardStatus>()''', "bleed field")

m = replace_once(m,
'''        val backup = Button("Backup catalog").apply { setOnAction { backupCatalog(stage) } }
        val exportSvg = Button("Export SVG").apply { setOnAction { exportSvg(stage) } }''',
'''        val backup = Button("Backup catalog").apply { setOnAction { backupCatalog(stage) } }
        val shareSidecar = Button("Share sidecar").apply {
            tooltip = Tooltip("Explicitly create a portable .card.json sidecar for the selected card. Normal saves use SQLite only.")
            setOnAction { createSharingSidecar() }
        }
        val exportSvg = Button("Export SVG").apply { setOnAction { exportSvg(stage) } }''', "share button")

m = replace_once(m,
'''        return ToolBar(open, Separator(), previous, next, Separator(), save, undo, redo, randomize, uiThemeButton, history, databaseInfo, backup, Separator(), exportSvg, exportPng, exportPdf, contactSheet)''',
'''        return ToolBar(open, Separator(), previous, next, Separator(), save, undo, redo, randomize, uiThemeButton, history, databaseInfo, backup, shareSidecar, Separator(), exportSvg, exportPng, exportPdf, contactSheet)''', "toolbar")

m = replace_once(m,
'''                    tooltip = Tooltip(if (root != null) relativePath(item) else item.toString())
                }
            }''',
'''                    tooltip = Tooltip(if (root != null) relativePath(item) else item.toString())
                    contextMenu = ContextMenu(
                        MenuItem("Open in Finder").apply { setOnAction { revealInFinder(item) } },
                        MenuItem("Copy Path").apply { setOnAction {
                            val content = ClipboardContent().apply { putString(item.toAbsolutePath().toString()) }
                            Clipboard.getSystemClipboard().setContent(content)
                        } }
                    )
                }
            }''', "folder context menu")

m = replace_once(m,
'''            children.add(Button("Reload schemes").apply { setOnAction { loadSchemes() } })
        })''',
'''            children.add(Button("From image").apply {
                tooltip = Tooltip("Choose the closest coordinated color scheme from the dominant artwork color.")
                setOnAction { applySchemeFromCurrentImage() }
            })
            children.add(Button("Reload schemes").apply { setOnAction { loadSchemes() } })
        })''', "image scheme button")

m = replace_once(m,
'''        form.children.add(row("Fit", imageMode))
        form.children.add(helperLabel("Drag the artwork to pan. Scroll to zoom. Double-click the artwork or use Reset to return to centered 1×.") )''',
'''        form.children.add(row("Fit", imageMode))
        imageBleedOverFrame.apply {
            tooltip = Tooltip("Extend artwork behind the surrounding card frame. The description panel remains above it; panel opacity controls how much artwork can show through there.")
            selectedProperty().addListener { _, _, _ -> if (!suppressEditorUpdates) updateFromEditor() }
        }
        form.children.add(imageBleedOverFrame)
        form.children.add(helperLabel("Drag the artwork to pan. Scroll to zoom. Double-click the artwork or use Reset to return to centered 1×."))''', "bleed UI")

m = replace_once(m,
'''            imageMode.value = currentData.imageMode
            zoom.value = currentData.imageZoom.coerceIn(0.1, 4.0)''',
'''            imageMode.value = currentData.imageMode
            imageBleedOverFrame.isSelected = currentData.imageBleedOverFrame
            zoom.value = currentData.imageZoom.coerceIn(0.1, 4.0)''', "populate bleed")

m = replace_once(m,
'''        currentData.imageMode = imageMode.value ?: currentData.imageMode
        currentData.imageZoom = zoom.value''',
'''        currentData.imageMode = imageMode.value ?: currentData.imageMode
        currentData.imageBleedOverFrame = imageBleedOverFrame.isSelected
        currentData.imageZoom = zoom.value''', "save bleed")

# SQLite is authoritative. Explicit sharing sidecars are never consulted as live storage.
m = replace_once(m,
'''        val data = Sidecar.load(path) ?: newCardDefaults().also { it.assetId = "" }''',
'''        val data = database?.dataSnapshotForPath(path) ?: newCardDefaults(path).also { it.assetId = "" }''', "search data")

start = m.index("    private fun resolveCardDataForSelection(path: Path): CardData?")
end = m.index("\n    private fun dataEquivalent", start)
m = m[:start] + '''    private fun resolveCardDataForSelection(path: Path): CardData? {
        val db = database ?: return newCardDefaults(path)
        val dbData = db.dataSnapshotForPath(path)
        if (dbData != null && hasMeaningfulCardData(dbData)) return dbData
        return newCardDefaults(path).also { defaults ->
            dbData?.assetId?.takeIf { it.isNotBlank() }?.let { defaults.assetId = it }
        }
    }
''' + m[end:]

start = m.index("    private fun newCardDefaults(): CardData")
end = m.index("\n    private fun select(", start)
m = m[:start] + '''    private fun newCardDefaults(path: Path? = null): CardData {
        val random = randomGenerator()
        val data = CardData(
            assetId = UUID.randomUUID().toString(),
            status = CardStatus.NEW,
            title = path?.fileName?.toString()?.substringBeforeLast('.', path.fileName.toString()) ?: "CARD NAME",
            cost = random.nextInt(0, 10).toString(),
            typeLine = listOf("CREATURE — MYSTIC", "LEGENDARY CHARACTER", "ARTIFACT — RELIC", "SORCERY — RITUAL", "SPELL — ARCANE", "ALLY — KNIGHT").random(random),
            rarity = weightedRarity(random),
            artist = artistPatterns.random(random),
            collectorNumber = nextUnusedCollectorNumber(random),
            stats = "${random.nextInt(0, 13)} / ${random.nextInt(0, 13)}",
            templateName = templates.randomOrNull(random)?.name.orEmpty(),
            backgroundOverlay = "",
            imageMode = ImageMode.COVER,
            imageBleedOverFrame = false
        )
        val derived = path?.let(ImageColorAnalyzer::dominantColor)
        val scheme = derived?.let { ImageColorAnalyzer.bestMatchingScheme(it, schemes) } ?: schemes.randomOrNull(random)
        scheme?.applyTo(data)
        return data
    }

    private fun weightedRarity(random: kotlin.random.Random): String = when (random.nextInt(100)) {
        in 0..49 -> "COMMON"
        in 50..74 -> "UNCOMMON"
        in 75..91 -> "RARE"
        in 92..97 -> "MYTHIC"
        else -> "LEGENDARY"
    }

    private fun nextUnusedCollectorNumber(random: kotlin.random.Random = randomGenerator(), total: Int = 100): String {
        val used = database?.usedCollectorNumbers(null).orEmpty()
        val available = (1..total).map { "%03d/%d".format(it, total) }.filterNot(used::contains)
        return available.randomOrNull(random) ?: "%03d/%d".format(random.nextInt(1, total + 1), total)
    }
''' + m[end:]

# Passive previews/sorting use catalog only and deterministic new-card initialization by path.
old_start = m.index("    private fun savedDataForPath(path: Path): CardData")
old_end = m.index("\n    private fun hasMeaningfulCardData", old_start)
m = m[:old_start] + '''    private fun savedDataForPath(path: Path): CardData {
        val normalized = path.toAbsolutePath().normalize()
        cardDataCache[normalized]?.let { return it.copy() }
        val result = database?.dataSnapshotForPath(normalized)?.takeIf(::hasMeaningfulCardData)?.copy()
            ?: newCardDefaults(normalized)
        cardDataCache[normalized] = result.copy()
        return result
    }
''' + m[old_end:]

m = m.replace('''            database?.dataSnapshotForPath(normalized) ?: Sidecar.load(normalized) ?: newCardDefaults()
        }.getOrDefault(newCardDefaults())''', '''            database?.dataSnapshotForPath(normalized) ?: newCardDefaults(normalized)
        }.getOrDefault(newCardDefaults(normalized))''')

# Legacy sidecars are imported once if needed, then removed. Explicit share sidecars are preserved.
m = replace_once(m,
'''                allImages.clear()
                allImages.addAll(task.value.images)
                visibleImages.clear()''',
'''                allImages.clear()
                allImages.addAll(task.value.images)
                migrateLegacySidecars(newDatabase, allImages)
                visibleImages.clear()''', "sidecar migration hook")

m = replace_once(m,
'''    private fun usedCollectorNumbersForCollection(excludingAssetId: String? = null): Set<String> {
        val used = linkedSetOf<String>()
        database?.usedCollectorNumbers(excludingAssetId)?.let(used::addAll)
        // Sidecars can contain saved data that has not yet been imported into SQLite.
        // Include those too so the duplicate check matches the whole collection.
        allImages.forEach { image ->
            val sidecar = Sidecar.load(image) ?: return@forEach
            if (excludingAssetId != null && sidecar.assetId == excludingAssetId) return@forEach
            sidecar.collectorNumber.trim().takeIf { it.isNotBlank() }?.let(used::add)
        }
        return used
    }''',
'''    private fun usedCollectorNumbersForCollection(excludingAssetId: String? = null): Set<String> =
        database?.usedCollectorNumbers(excludingAssetId).orEmpty()

    private fun migrateLegacySidecars(db: CollectionDatabase, images: List<Path>) {
        var imported = 0
        var removed = 0
        images.forEach { image ->
            if (!Sidecar.exists(image) || Sidecar.isExplicitShare(image)) return@forEach
            val legacy = Sidecar.load(image)
            if (legacy != null) {
                val existing = db.dataSnapshotForPath(image)
                if (existing == null || !hasMeaningfulCardData(existing)) {
                    db.save(image, legacy)
                    imported++
                }
            }
            if (Sidecar.remove(image)) removed++
        }
        if (removed > 0) db.recordActivity("", "SIDECAR_MIGRATION", "imported=$imported removed=$removed")
    }

    private fun createSharingSidecar() {
        val path = imagesCurrentPath() ?: return
        if (!saveCurrent(showStatus = false)) return
        runCatching { Sidecar.createForSharing(path, currentData) }
            .onSuccess { target -> statusBarLabel.text = "Sharing sidecar created • ${target.fileName}" }
            .onFailure { showError("Could not create sharing sidecar", it) }
    }

    private fun applySchemeFromCurrentImage() {
        val path = imagesCurrentPath() ?: return
        val dominant = ImageColorAnalyzer.dominantColor(path) ?: run {
            statusBarLabel.text = "Could not determine a useful dominant image color."
            return
        }
        val scheme = ImageColorAnalyzer.bestMatchingScheme(dominant, schemes) ?: return
        captureUndoSnapshot()
        suppressEditorUpdates = true
        try {
            scheme.applyTo(currentData)
            schemeChoice.value = scheme
            populateColorPickersFromData()
        } finally { suppressEditorUpdates = false }
        updateFromEditor(renderPreview = false)
        render()
        statusBarLabel.text = "Matched image color to scheme '${scheme.name}'"
    }''', "collector/sidecar block")

# Normal save: database only.
m = m.replace("            Sidecar.save(path, currentData)\n", "")

MAIN.write_text(m)

r = RENDERER.read_text()
r = replace_once(r,
'''        if (templateView != null) outer.children.add(templateView)

        if (backgroundOverlay != null && data.backgroundOverlayPlacement == OverlayPlacement.FRAMES_ONLY) {''',
'''        if (templateView != null) outer.children.add(templateView)

        // Optional borderless-art treatment: place a second copy of the artwork behind
        // the normal card content. Text boxes remain above it; the description panel's
        // existing opacity therefore controls how much artwork can show through there.
        val bleedImageView = if (data.imageBleedOverFrame && image != null) ImageView(image).apply {
            isSmooth = true
            isManaged = false
            isMouseTransparent = true
            updateBleedImageView(this, image, data, template)
        } else null
        if (bleedImageView != null) outer.children.add(bleedImageView)

        if (backgroundOverlay != null && data.backgroundOverlayPlacement == OverlayPlacement.FRAMES_ONLY) {''', "bleed layer")

r = replace_once(r,
'''        val imageView = ImageView(image).apply { isSmooth = true; isManaged = false }
        updateImageView(imageView, image, data, template)''',
'''        val imageView = ImageView(image).apply { isSmooth = true; isManaged = false }
        if (bleedImageView != null) imageView.properties["cardforge.bleedImageView"] = bleedImageView
        updateImageView(imageView, image, data, template)''', "bleed link")

r = replace_once(r,
'''    fun updateImageView(view: ImageView, image: Image?, data: CardData, template: CardTemplate) {
        val layout = imageLayout(image, data, template)
        view.isPreserveRatio = false
        view.fitWidth = layout.width
        view.fitHeight = layout.height
        view.translateX = layout.x
        view.translateY = layout.y
    }''',
'''    fun updateImageView(view: ImageView, image: Image?, data: CardData, template: CardTemplate) {
        val layout = imageLayout(image, data, template)
        view.isPreserveRatio = false
        view.fitWidth = layout.width
        view.fitHeight = layout.height
        view.translateX = layout.x
        view.translateY = layout.y
        (view.properties["cardforge.bleedImageView"] as? ImageView)?.let {
            updateBleedImageView(it, image, data, template)
        }
    }

    private fun updateBleedImageView(view: ImageView, image: Image?, data: CardData, template: CardTemplate) {
        val layout = imageLayout(image, data, template)
        view.isPreserveRatio = false
        view.fitWidth = layout.width
        view.fitHeight = layout.height
        view.translateX = template.art.x + layout.x
        view.translateY = template.art.y + layout.y
    }''', "bleed update")

RENDERER.write_text(r)
print("Applied Card Forge 0.14.4 integration patch")
