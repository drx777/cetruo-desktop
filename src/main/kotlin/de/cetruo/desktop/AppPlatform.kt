package de.cetruo.desktop

import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Screen
import javafx.stage.Stage
import java.awt.Taskbar
import javax.imageio.ImageIO
import kotlin.math.min

/** Platform/window concerns kept separate from card and collection state. */
object AppPlatform {
    data class WindowSize(val width: Double, val height: Double)

    fun initialWindowSize(): WindowSize {
        val visual = Screen.getPrimary().visualBounds
        return initialWindowSizeFor(visual.width, visual.height)
    }

    fun initialWindowSizeFor(visualWidth: Double, visualHeight: Double): WindowSize {
        val desiredWidth = 1660.0
        val desiredHeight = 1040.0
        return WindowSize(
            width = min(desiredWidth, visualWidth * 0.94).coerceAtLeast(1180.0).coerceAtMost(visualWidth),
            height = min(desiredHeight, visualHeight * 0.93).coerceAtLeast(760.0).coerceAtMost(visualHeight)
        )
    }

    fun installWindowIcon(stage: Stage, resourceOwner: Class<*>) {
        resourceOwner.getResourceAsStream("/icons/card-forge-icon.png")?.use {
            stage.icons.add(Image(it))
        }
    }

    fun setApplicationDockIcon(resourceOwner: Class<*>) {
        if (!System.getProperty("os.name").contains("Mac", ignoreCase = true)) return
        val icon = resourceOwner.getResourceAsStream("/icons/card-forge-icon.png")?.use(ImageIO::read) ?: return
        runCatching {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(icon)
            }
        }
        runCatching {
            val applicationClass = Class.forName("com.apple.eawt.Application")
            val application = applicationClass.getMethod("getApplication").invoke(null)
            applicationClass.getMethod("setDockIconImage", java.awt.Image::class.java).invoke(application, icon)
        }
    }

    fun attachStylesheet(scene: Scene, resourceOwner: Class<*>) {
        resourceOwner.getResource("/cardforge.css")?.toExternalForm()?.let { scene.stylesheets.add(it) }
    }
}
