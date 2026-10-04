package com.example.cardforge

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.Screen
import javafx.stage.Stage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

/** Displays the selected source artwork without card framing or editor transforms. */
class SourceImageInspector(
    private val resourceOwner: Class<*>,
    private val isDarkTheme: () -> Boolean
) {
    private var stage: Stage? = null

    fun show(owner: Stage?, source: Path, onUnavailable: () -> Unit = {}) {
        val normalized = source.toAbsolutePath().normalize()
        if (!Files.isRegularFile(normalized)) {
            onUnavailable()
            return
        }

        close()

        val image = Image(normalized.toUri().toString(), true)
        val imageView = ImageView(image).apply {
            isPreserveRatio = true
            isSmooth = true
        }
        val details = Label("${normalized.fileName} · Loading original image…").apply {
            styleClass.add("browser-meta")
        }
        val scroll = ScrollPane(imageView).apply {
            isPannable = true
            isFitToWidth = false
            isFitToHeight = false
            style = "-fx-background-color:transparent;"
        }
        val root = BorderPane(scroll).apply {
            padding = Insets(10.0)
            top = HBox(10.0, details, Region(), Label("Native size · Esc to close")).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(children[1], Priority.ALWAYS)
            }
            style = if (isDarkTheme()) {
                "-fx-background-color:#20242A;"
            } else {
                "-fx-background-color:#F6F7F9;"
            }
        }

        val screen = owner?.let {
            Screen.getScreensForRectangle(
                it.x,
                it.y,
                it.width.coerceAtLeast(1.0),
                it.height.coerceAtLeast(1.0)
            ).firstOrNull()
        } ?: Screen.getPrimary()
        val bounds = screen.visualBounds

        val inspector = Stage().apply {
            owner?.let { initOwner(it) }
            title = "Card Forge · Source Image · ${normalized.fileName}"
            owner?.icons?.firstOrNull()?.let { icons.add(it) }
            scene = Scene(
                root,
                (bounds.width * 0.85).coerceAtMost(1500.0).coerceAtLeast(640.0),
                (bounds.height * 0.85).coerceAtMost(1100.0).coerceAtLeast(480.0)
            ).also { inspectorScene ->
                AppPlatform.attachStylesheet(inspectorScene, resourceOwner)
                inspectorScene.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                    if (event.code == KeyCode.ESCAPE) {
                        close()
                        event.consume()
                    }
                }
            }
            setOnHidden {
                if (stage === this) stage = null
            }
        }
        stage = inspector

        fun refreshDetails() {
            details.text = when {
                image.isError -> "${normalized.fileName} · Could not load original image"
                image.progress < 1.0 -> "${normalized.fileName} · Loading original image…"
                else -> "${normalized.fileName} · ${image.width.roundToInt()} × ${image.height.roundToInt()} px"
            }
        }
        image.progressProperty().addListener { _, _, _ -> refreshDetails() }
        image.errorProperty().addListener { _, _, _ -> refreshDetails() }
        refreshDetails()

        inspector.show()
        inspector.centerOnScreen()
    }

    fun close() {
        stage?.close()
        stage = null
    }
}
