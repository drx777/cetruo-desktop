#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main_path = root / "src/main/kotlin/com/example/cardforge/Main.kt"
renderer_path = root / "src/main/kotlin/com/example/cardforge/CardRenderer.kt"
main = main_path.read_text()
renderer = renderer_path.read_text()

def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"Could not locate {label}")
    return text.replace(old, new, 1)

main = replace_once(main,
'''    private val imageBleedOverFrame = CheckBox("Artwork bleeds over frame")
    private val statusChoice = ComboBox<CardStatus>()''',
'''    private val imageBleedOverFrame = CheckBox("Artwork bleeds over frame")
    private val imageBleedOpacity = Slider(0.0, 1.0, 1.0)
    private val statusChoice = ComboBox<CardStatus>()''', "bleed opacity field")

main = replace_once(main,
'''        form.children.add(rowWithDice("Artist", textField("artist")) { randomizeArtistPattern() })
        form.children.add(rowWithDice("Set", textField("setName")) { randomizeSetName() })
        form.children.add(rowWithDice("Number", textField("collectorNumber")) { randomizeCollectorNumber() })''',
'''        form.children.add(rowWithDice("Artist", textField("artist")) { randomizeArtistPattern() })
        val setNameField = textField("setName")
        form.children.add(rowWithDice("Set", setNameField) { randomizeSetName() })
        form.children.add(Button("Apply set name to all cards").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("Apply this card's set name to every image in the collection and update collector-number totals.")
            setOnAction { applyCurrentSetNameToAllCards() }
        })
        form.children.add(rowWithDice("Number", textField("collectorNumber")) { randomizeCollectorNumber() })''', "set bulk button")

main = replace_once(main,
'''        form.children.add(Button("Reset card to collection default").apply {
            maxWidth = Double.MAX_VALUE
            setOnAction { templateOverride.isSelected = false }
        })''',
'''        form.children.add(Button("Reset card to collection default").apply {
            maxWidth = Double.MAX_VALUE
            setOnAction { templateOverride.isSelected = false }
        })
        form.children.add(Button("Apply collection default to all cards").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("Remove every per-card template override so all cards follow the collection default.")
            setOnAction { applyCollectionDefaultTemplateToAllCards() }
        })''', "template bulk button")

main = replace_once(main,
'''        form.children.add(imageBleedOverFrame)
        form.children.add(helperLabel("Drag the artwork to pan. Scroll to zoom. Double-click the artwork or use Reset to return to centered 1×."))''',
'''        form.children.add(imageBleedOverFrame)
        form.children.add(sliderRow("Bleed opacity", imageBleedOpacity, Label(), "%.2f"))
        installSliderReset(imageBleedOpacity, 1.0)
        imageBleedOpacity.valueProperty().addListener { _, _, _ -> if (!suppressEditorUpdates) updateFromEditor() }
        form.children.add(helperLabel("Bleed is clipped to the card boundary. Its opacity affects only artwork outside the normal image frame."))
        form.children.add(helperLabel("Drag the artwork to pan. Scroll to zoom. Double-click the artwork or use Reset to return to centered 1×."))''', "bleed opacity UI")

main = replace_once(main,
'''            imageMode.value = currentData.imageMode
            imageBleedOverFrame.isSelected = currentData.imageBleedOverFrame
            zoom.value = currentData.imageZoom.coerceIn(0.1, 4.0)''',
'''            imageMode.value = currentData.imageMode
            imageBleedOverFrame.isSelected = currentData.imageBleedOverFrame
            imageBleedOpacity.value = currentData.imageBleedOpacity.coerceIn(0.0, 1.0)
            zoom.value = currentData.imageZoom.coerceIn(0.1, 4.0)''', "populate bleed opacity")

main = replace_once(main,
'''        currentData.imageMode = imageMode.value ?: currentData.imageMode
        currentData.imageBleedOverFrame = imageBleedOverFrame.isSelected
        currentData.imageZoom = zoom.value''',
'''        currentData.imageMode = imageMode.value ?: currentData.imageMode
        currentData.imageBleedOverFrame = imageBleedOverFrame.isSelected
        currentData.imageBleedOpacity = imageBleedOpacity.value.coerceIn(0.0, 1.0)
        currentData.imageZoom = zoom.value''', "save bleed opacity")

# Add bulk helpers before usedCollectorNumbersForCollection.
needle = '''    private fun usedCollectorNumbersForCollection(excludingAssetId: String? = null): Set<String> =
        database?.usedCollectorNumbers(excludingAssetId).orEmpty()
'''
helpers = '''    private fun collectorPrefix(value: String): Int? = value.substringBefore('/').trim().toIntOrNull()?.takeIf { it > 0 }

    private fun collectionCardData(path: Path): CardData {
        val normalized = path.toAbsolutePath().normalize()
        if (currentLoadedPath?.toAbsolutePath()?.normalize() == normalized) return currentData.copy()
        return cardDataCache[normalized]?.copy()
            ?: database?.dataSnapshotForPath(normalized)?.copy()
            ?: newCardDefaults(normalized)
    }

    private fun setSize(setName: String): Int = allImages.count { collectionCardData(it).setName == setName }.coerceAtLeast(1)

    private fun collectorNumberForSet(existing: String, setName: String, usedPrefixes: MutableSet<Int>): String {
        val total = setSize(setName)
        var prefix = collectorPrefix(existing)?.takeIf { it in 1..total && it !in usedPrefixes }
        if (prefix == null) prefix = (1..total).firstOrNull { it !in usedPrefixes } ?: 1
        usedPrefixes.add(prefix)
        val width = maxOf(3, total.toString().length)
        return "%0${width}d/%d".format(prefix, total)
    }

    private fun normalizeCollectorTotalsForSets(setNames: Set<String>) {
        val db = database ?: return
        setNames.filter { it.isNotBlank() }.forEach { setName ->
            val members = allImages.map { it.toAbsolutePath().normalize() }
                .filter { collectionCardData(it).setName == setName }
            val total = members.size.coerceAtLeast(1)
            val width = maxOf(3, total.toString().length)
            val used = mutableSetOf<Int>()
            members.forEach { path ->
                val data = collectionCardData(path)
                var prefix = collectorPrefix(data.collectorNumber)?.takeIf { it in 1..total && it !in used }
                if (prefix == null) prefix = (1..total).firstOrNull { it !in used } ?: 1
                used.add(prefix)
                data.collectorNumber = "%0${width}d/%d".format(prefix, total)
                db.save(path, data)
                cardDataCache[path] = data.copy()
                searchIndex[relativePath(path)] = searchableTextFromData(path, data)
                if (currentLoadedPath?.toAbsolutePath()?.normalize() == path) currentData.collectorNumber = data.collectorNumber
            }
        }
        if (currentIndex in visibleImages.indices) populateEditor()
        refreshBrowser()
        render()
    }

    private fun applyCurrentSetNameToAllCards() {
        val db = database ?: return
        if (currentIndex !in visibleImages.indices) return
        updateFromEditor(renderPreview = false)
        val newSetName = currentData.setName.trim()
        if (newSetName.isBlank()) return
        allImages.forEach { rawPath ->
            val path = rawPath.toAbsolutePath().normalize()
            val data = collectionCardData(path)
            data.setName = newSetName
            db.save(path, data)
            cardDataCache[path] = data.copy()
            searchIndex[relativePath(path)] = searchableTextFromData(path, data)
            if (currentLoadedPath?.toAbsolutePath()?.normalize() == path) currentData.setName = newSetName
        }
        normalizeCollectorTotalsForSets(setOf(newSetName))
        statusBarLabel.text = "Applied set '$newSetName' to ${allImages.size} cards."
    }

    private fun applyCollectionDefaultTemplateToAllCards() {
        val db = database ?: return
        if (collectionDefaultTemplateName.isBlank()) return
        allImages.forEach { rawPath ->
            val path = rawPath.toAbsolutePath().normalize()
            val data = collectionCardData(path)
            data.templateName = ""
            db.save(path, data)
            cardDataCache[path] = data.copy()
            searchIndex[relativePath(path)] = searchableTextFromData(path, data)
            if (currentLoadedPath?.toAbsolutePath()?.normalize() == path) currentData.templateName = ""
        }
        populateEditor()
        loadTemplateAndOverlay()
        refreshBrowser()
        render()
        statusBarLabel.text = "Applied collection default template to ${allImages.size} cards."
    }

    private fun usedCollectorNumbersForCollection(excludingAssetId: String? = null): Set<String> =
        database?.usedCollectorNumbers(excludingAssetId).orEmpty()
'''
main = replace_once(main, needle, helpers, "bulk helper insertion")

# Keep totals correct when a card moves between sets. Do this after its primary save, then
# normalize both affected sets. The normalizer preserves usable prefixes and only repairs conflicts.
main = replace_once(main,
'''            val result = db.save(path, currentData)
            currentData.assetId = result.assetId''',
'''            val previousSetName = previous?.setName.orEmpty()
            val result = db.save(path, currentData)
            currentData.assetId = result.assetId
            val affectedSets = setOf(previousSetName, currentData.setName).filter { it.isNotBlank() }.toSet()
            if (affectedSets.isNotEmpty() && previousSetName != currentData.setName) normalizeCollectorTotalsForSets(affectedSets)''', "set total normalization on save")

# New cards get a collector number whose denominator reflects the current default set size + this card.
main = replace_once(main,
'''            collectorNumber = nextUnusedCollectorNumber(random),''',
'''            collectorNumber = nextUnusedCollectorNumber(random, (allImages.count { collectionCardData(it).setName == CardData().setName }).coerceAtLeast(1)),''', "new-card set total")

main_path.write_text(main)

renderer = replace_once(renderer,
'''            style = "-fx-background-color:${data.backgroundColor};-fx-background-radius:${data.cornerRadius}px;" +
                "-fx-border-color:${data.accentColor};-fx-border-width:${data.borderWidth}px;" +
                "-fx-border-radius:${data.cornerRadius}px;"
        }''',
'''            style = "-fx-background-color:${data.backgroundColor};-fx-background-radius:${data.cornerRadius}px;" +
                "-fx-border-color:${data.accentColor};-fx-border-width:${data.borderWidth}px;" +
                "-fx-border-radius:${data.cornerRadius}px;"
            // The bleed copy must never paint outside the physical card silhouette.
            clip = Rectangle(template.width, template.height).apply {
                arcWidth = data.cornerRadius * 2.0
                arcHeight = data.cornerRadius * 2.0
            }
        }''', "card-boundary clip")

renderer = replace_once(renderer,
'''            isMouseTransparent = true
            updateBleedImageView(this, image, data, template)''',
'''            isMouseTransparent = true
            opacity = data.imageBleedOpacity.coerceIn(0.0, 1.0)
            updateBleedImageView(this, image, data, template)''', "bleed opacity render")

renderer = replace_once(renderer,
'''        view.isPreserveRatio = false
        view.fitWidth = layout.width''',
'''        view.isPreserveRatio = false
        view.opacity = data.imageBleedOpacity.coerceIn(0.0, 1.0)
        view.fitWidth = layout.width''', "bleed opacity refresh")

renderer_path.write_text(renderer)
print("Applied collection bulk actions, set-aware collector totals, and clipped configurable artwork bleed.")
