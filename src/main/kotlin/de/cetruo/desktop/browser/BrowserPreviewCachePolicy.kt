package de.cetruo.desktop.browser

import de.cetruo.desktop.*
object BrowserPreviewCachePolicy {
    fun originalIsCurrent(
        cachedSize: Long,
        cachedModified: Long,
        sourceSize: Long,
        sourceModified: Long
    ): Boolean =
        cachedSize == sourceSize && cachedModified == sourceModified

    fun cardIsCurrent(
        cachedSize: Long,
        cachedModified: Long,
        cachedSignature: String,
        sourceSize: Long,
        sourceModified: Long,
        expectedSignature: String
    ): Boolean =
        originalIsCurrent(cachedSize, cachedModified, sourceSize, sourceModified) &&
            cachedSignature == expectedSignature
}
