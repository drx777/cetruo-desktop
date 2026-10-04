package com.example.cardforge

import javafx.scene.paint.Color
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Color-only scheme. Keeping this separate from CardData means schemes stay easy to author by hand.
 */
enum class SchemeTone { AUTO, LIGHT, DARK }

data class ColorScheme(
    val name: String,
    val description: String = "",
    val tone: SchemeTone = SchemeTone.AUTO,
    val backgroundColor: String,
    val panelColor: String,
    val frameColor: String,
    val accentColor: String,
    val overlayColor: String = accentColor,
    val textColor: String,
    val darkTextColor: String,
    val imagePadColor: String
) {
    val resolvedTone: SchemeTone
        get() = when (tone) {
            SchemeTone.LIGHT, SchemeTone.DARK -> tone
            SchemeTone.AUTO -> runCatching {
                if (relativeLuminance(backgroundColor) >= 0.52) SchemeTone.LIGHT else SchemeTone.DARK
            }.getOrDefault(SchemeTone.DARK)
        }

    fun applyTo(data: CardData) {
        data.schemeName = name
        data.backgroundColor = backgroundColor
        data.panelColor = panelColor
        data.frameColor = frameColor
        data.accentColor = accentColor
        data.overlayColor = overlayColor
        data.textColor = textColor
        data.darkTextColor = darkTextColor
        data.imagePadColor = imagePadColor
    }

    private fun relativeLuminance(hex: String): Double {
        val c = Color.web(hex)
        fun channel(v: Double): Double = if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}

object SchemeRepository {
    const val DIRECTORY_NAME = "schemes"

    private fun candidateDirectories(): List<Path> {
        val result = linkedSetOf<Path>()
        System.getProperty("cardforge.schemesDir")
            ?.takeIf { it.isNotBlank() }
            ?.let { result.add(Paths.get(it).toAbsolutePath().normalize()) }
        runCatching {
            Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        }.getOrNull()?.let { result.add(it.resolve(DIRECTORY_NAME).normalize()) }
        runCatching {
            val location = Paths.get(SchemeRepository::class.java.protectionDomain.codeSource.location.toURI())
                .toAbsolutePath().normalize()
            val base = if (Files.isDirectory(location)) location else location.parent
            if (base != null) result.add(base.resolve(DIRECTORY_NAME).normalize())
        }.getOrNull()
        return result.toList()
    }

    fun load(): Pair<List<ColorScheme>, List<String>> {
        val directory = candidateDirectories().firstOrNull { dir ->
            Files.isDirectory(dir) && runCatching {
                Files.list(dir).use { stream -> stream.anyMatch { path ->
                    Files.isRegularFile(path) && path.fileName.toString().endsWith(".json", ignoreCase = true)
                } }
            }.getOrDefault(false)
        }

        if (directory == null) {
            return emptyList<ColorScheme>() to listOf("No $DIRECTORY_NAME directory found. Set -Dcardforge.schemesDir to override the location.")
        }

        val errors = mutableListOf<String>()
        val schemes = mutableListOf<ColorScheme>()
        Files.list(directory).use { stream ->
            stream.filter { path ->
                Files.isRegularFile(path) && path.fileName.toString().endsWith(".json", ignoreCase = true)
            }.sorted(compareBy { path -> path.fileName.toString().lowercase() }).forEach { path ->
                runCatching {
                    JsonSupport.mapper.readValue(Files.readString(path), ColorScheme::class.java)
                }.onSuccess { scheme ->
                    schemes.add(scheme)
                    runCatching { SchemeContrast.warnings(scheme) }.getOrDefault(emptyList()).forEach { warning ->
                        errors.add("${path.fileName}: contrast warning — $warning")
                    }
                }.onFailure { error -> errors.add("${path.fileName}: ${error.message ?: "invalid JSON"}") }
            }
        }
        val result = if (schemes.isEmpty()) listOf(
            ColorScheme(
                name = "Classic",
                tone = SchemeTone.DARK,
                description = "Built-in fallback scheme. Add JSON schemes under schemes/ to extend the collection.",
                backgroundColor = "#161B22",
                panelColor = "#EFE8D7",
                frameColor = "#D9C28E",
                accentColor = "#8C8068",
                overlayColor = "#C9B37A",
                textColor = "#29251F",
                darkTextColor = "#F3EAD6",
                imagePadColor = "#0A0D10"
            )
        ) else schemes
        return result.sortedWith(compareBy<ColorScheme>({ if (it.resolvedTone == SchemeTone.LIGHT) 0 else 1 }, { it.name.lowercase() })) to errors
    }
}


object SchemeContrast {
    data class Check(val pair: String, val ratio: Double, val minimum: Double) {
        val ok: Boolean get() = ratio >= minimum
    }

    fun checks(scheme: ColorScheme): List<Check> = listOf(
        Check("panel / text", contrastRatio(scheme.panelColor, scheme.textColor), 4.5),
        Check("background / light text", contrastRatio(scheme.backgroundColor, scheme.darkTextColor), 3.0),
        Check("background / frame", contrastRatio(scheme.backgroundColor, scheme.frameColor), 3.0),
        Check("background / overlay", contrastRatio(scheme.backgroundColor, scheme.overlayColor), 2.5),
        Check("panel / overlay", contrastRatio(scheme.panelColor, scheme.overlayColor), 2.5),
        Check("panel / accent", contrastRatio(scheme.panelColor, scheme.accentColor), 3.0)
    )

    fun warnings(scheme: ColorScheme): List<String> = checks(scheme)
        .filterNot { it.ok }
        .map { "${it.pair} ${"%.2f".format(it.ratio)}:1 < ${"%.1f".format(it.minimum)}:1" }

    private fun contrastRatio(a: String, b: String): Double {
        fun luminance(hex: String): Double {
            val c = Color.web(hex)
            fun channel(v: Double): Double = if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        }
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
