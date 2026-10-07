package de.cetruo.desktop

import java.math.BigInteger
import java.nio.file.Path
import kotlin.math.min

enum class BrowserSort {
    NAME,
    LAST_WORD,
    COLLECTOR_NUMBER,
    FOLDER,
    STATUS,
    FILE_NAME
}

class BrowserImageSorter(
    private val cardDataFor: (Path) -> CardData,
    private val relativePathFor: (Path) -> String
) {
    fun sort(paths: MutableList<Path>, sort: BrowserSort, descending: Boolean) {
        if (paths.size <= 1) return

        val comparator = Comparator<Path> { left, right ->
            val leftData = cardDataFor(left)
            val rightData = cardDataFor(right)
            val result = when (sort) {
                BrowserSort.NAME -> compareNaturally(leftData.title, rightData.title)
                BrowserSort.LAST_WORD -> compareNaturally(lastWord(leftData.title), lastWord(rightData.title))
                BrowserSort.COLLECTOR_NUMBER -> compareCollectorNumbers(leftData.collectorNumber, rightData.collectorNumber)
                BrowserSort.FOLDER -> compareNaturally(relativeFolder(left), relativeFolder(right))
                BrowserSort.STATUS -> compareNaturally(leftData.status.name, rightData.status.name)
                BrowserSort.FILE_NAME -> compareNaturally(left.fileName.toString(), right.fileName.toString())
            }

            if (result != 0) result else compareNaturally(relativePathFor(left), relativePathFor(right))
        }

        paths.sortWith(if (descending) comparator.reversed() else comparator)
    }

    private fun relativeFolder(path: Path): String =
        path.parent?.let(relativePathFor).orEmpty()

    companion object {
        internal fun lastWord(value: String): String =
            value.trim().split(Regex("\\s+")).lastOrNull().orEmpty()

        internal fun compareNaturally(a: String, b: String): Int {
            val aa = a.trim()
            val bb = b.trim()
            val tokenPattern = Regex("(\\d+|\\D+)")
            val aTokens = tokenPattern.findAll(aa).toList()
            val bTokens = tokenPattern.findAll(bb).toList()
            val count = min(aTokens.size, bTokens.size)

            for (i in 0 until count) {
                val ax = aTokens[i].value
                val bx = bTokens[i].value
                val comparison = if (ax.all(Char::isDigit) && bx.all(Char::isDigit)) {
                    numericToken(ax).compareTo(numericToken(bx))
                } else {
                    ax.compareTo(bx, ignoreCase = true)
                }
                if (comparison != 0) return comparison
            }

            return aTokens.size.compareTo(bTokens.size).takeIf { it != 0 }
                ?: aa.compareTo(bb, ignoreCase = true)
        }

        internal fun compareCollectorNumbers(a: String, b: String): Int {
            val left = parseCollectorNumber(a)
            val right = parseCollectorNumber(b)

            val numberComparison = left.first.compareTo(right.first)
            if (numberComparison != 0) return numberComparison

            val totalComparison = left.second.compareTo(right.second)
            return if (totalComparison != 0) totalComparison else compareNaturally(a, b)
        }

        private fun numericToken(value: String): BigInteger =
            value.trimStart('0').ifBlank { "0" }.toBigIntegerOrNull() ?: BigInteger.ZERO

        private fun parseCollectorNumber(value: String): Pair<Int, Int> {
            val match = Regex("^(\\d+)\\s*/\\s*(\\d+)").find(value.trim())
            return if (match != null) {
                (match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE) to
                    (match.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE)
            } else {
                Int.MAX_VALUE to Int.MAX_VALUE
            }
        }
    }
}
