package de.cetruo.desktop

import javafx.scene.control.ComboBox
import javafx.scene.control.TextField
import kotlin.random.Random

class CardRandomizationController(
    private val fields: Map<String, TextField>,
    private val schemeChoice: ComboBox<ColorScheme>,
    private val backgroundOverlayChoice: ComboBox<BackgroundOverlay>,
    private val currentData: () -> CardData,
    private val hasSelection: () -> Boolean,
    private val hasDatabase: () -> Boolean,
    private val schemes: () -> List<ColorScheme>,
    private val overlays: () -> List<BackgroundOverlay>,
    private val randomGenerator: () -> Random,
    private val usedCollectorNumbers: (assetId: String) -> Set<String>,
    private val randomArtistPattern: () -> String,
    private val captureUndo: () -> Unit,
    private val withSuppressedUpdates: (() -> Unit) -> Unit,
    private val populateColors: () -> Unit,
    private val recalculatePanControls: (syncFromData: Boolean) -> Unit,
    private val updateFromEditor: (renderPreview: Boolean) -> Unit,
    private val render: () -> Unit,
    private val loadBackgroundOverlayImage: () -> Unit,
    private val setStatus: (String) -> Unit,
    private val currentPathLabel: () -> String
) {
    fun randomizeStyleAndNumbers() {
        if (!hasSelection()) return
        captureUndo()
        val values = CardRandomizer.styleAndNumbers(schemes(), randomGenerator())
        withSuppressedUpdates {
            values.scheme?.let {
                it.applyTo(currentData())
                schemeChoice.value = it
            }
            currentData().cost = values.cost
            currentData().stats = values.stats
            fields["cost"]?.text = values.cost
            fields["stats"]?.text = values.stats
            populateColors()
        }
        recalculatePanControls(true)
        updateFromEditor(false)
        render()
        setStatus("Randomized scheme and numeric values • ${currentPathLabel()}")
    }

    fun randomizeScheme() {
        if (!hasSelection()) return
        val scheme = CardRandomizer.scheme(schemes(), randomGenerator()) ?: return
        captureUndo()
        withSuppressedUpdates {
            scheme.applyTo(currentData())
            schemeChoice.value = scheme
            populateColors()
        }
        updateFromEditor(false)
        render()
    }

    fun randomizeCost() =
        randomizeTextField("cost", CardRandomizer.cost(randomGenerator()))

    fun randomizeStats() =
        randomizeTextField("stats", CardRandomizer.stats(randomGenerator()))

    fun randomizeCollectorNumber() {
        if (!hasSelection() || !hasDatabase()) return
        val data = currentData()
        val value = CardRandomizer.collectorNumber(
            current = data.collectorNumber,
            used = usedCollectorNumbers(data.assetId),
            random = randomGenerator()
        )
        if (value == null) {
            setStatus("No unused collector numbers remain in this collection.")
            return
        }
        randomizeTextField("collectorNumber", value)
    }

    fun randomizeArtistPattern() =
        randomizeTextField("artist", randomArtistPattern())

    fun randomizeTitle() =
        randomizeTextField("title", CardRandomizer.title(randomGenerator()))

    fun randomizeTypeLine() =
        randomizeTextField("typeLine", CardRandomizer.typeLine(randomGenerator()))

    fun randomizeRarity() =
        randomizeTextField("rarity", CardRandomizer.rarity(randomGenerator()))

    fun randomizeSetName() =
        randomizeTextField("setName", CardRandomizer.setName(randomGenerator()))

    fun randomizeOverlay() {
        if (!hasSelection()) return
        val option = CardRandomizer.overlay(overlays(), randomGenerator()) ?: return
        captureUndo()
        withSuppressedUpdates {
            backgroundOverlayChoice.value = option
            currentData().backgroundOverlay = option.path?.fileName?.toString().orEmpty()
            loadBackgroundOverlayImage()
        }
        updateFromEditor(false)
        render()
    }

    private fun randomizeTextField(key: String, value: String) {
        if (!hasSelection()) return
        captureUndo()
        withSuppressedUpdates {
            val data = currentData()
            when (key) {
                "title" -> data.title = value
                "cost" -> data.cost = value
                "typeLine" -> data.typeLine = value
                "rarity" -> data.rarity = value
                "stats" -> data.stats = value
                "artist" -> data.artist = value
                "setName" -> data.setName = value
                "collectorNumber" -> data.collectorNumber = value
                else -> error("Unsupported randomized field: $key")
            }
            fields[key]?.text = value
        }
        updateFromEditor(true)
    }
}
