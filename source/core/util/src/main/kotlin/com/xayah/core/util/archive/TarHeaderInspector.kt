package com.xayah.core.util.archive

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object TarHeaderInspector {
    enum class LinkType(val value: Int) {
        SYMBOLIC(1),
        HARD(2);

        companion object {
            fun fromValue(value: Int): LinkType = entries.first { it.value == value }
        }
    }

    data class Link(val type: LinkType, val path: String, val target: String)

    data class Result(val links: List<Link>, val skippedEntries: Int)

    fun inspect(
        input: InputStream,
        requiredPrefix: String = "",
        excludedPathPrefixes: Set<String> = emptySet(),
        excludedNamePrefixes: Set<String> = emptySet(),
        onApprovedEntry: (String) -> Unit,
    ): Result {
        val links = mutableListOf<Link>()
        var skippedEntries = 0
        TarArchiveInputStream(BufferedInputStream(input)).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                check(tar.canReadEntryData(entry)) { "Unsupported tar entry: ${entry.name}" }
                if (isRootDirectory(entry.name, entry.isDirectory)) {
                    onApprovedEntry(entry.name)
                    continue
                }
                val normalized = normalize(entry.name) ?: error("Unsafe archive path: ${entry.name}")
                if (requiredPrefix.isNotEmpty()) {
                    check(normalized.substringBefore('/') == requiredPrefix) { "Unexpected archive root: ${entry.name}" }
                }
                if (isExcluded(normalized, excludedPathPrefixes, excludedNamePrefixes)) continue
                when {
                    entry.isSymbolicLink -> links += Link(LinkType.SYMBOLIC, normalized, entry.linkName)
                    entry.isLink -> links += Link(LinkType.HARD, normalized, entry.linkName)
                    entry.isDirectory || entry.linkFlag in REGULAR_FILE_TYPES -> onApprovedEntry(entry.name)
                    else -> skippedEntries++
                }
            }
        }

        val symbolicLinks = links.asSequence()
            .filter { it.type == LinkType.SYMBOLIC }
            .mapTo(hashSetOf()) { it.path }
        val safeLinks = links.filter { link ->
            val safe = ancestors(link.path).none(symbolicLinks::contains) && isValidLinkTarget(link.target)
            if (!safe) skippedEntries++
            safe
        }
        return Result(safeLinks, skippedEntries)
    }

    private fun normalize(entry: String): String? {
        if (entry.startsWith('/') || '\u0000' in entry || entry.toByteArray(StandardCharsets.UTF_8).size > MAX_PATH_BYTES) return null
        val segments = entry.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.isEmpty() || ".." in segments) return null
        if (segments.any { it.toByteArray(StandardCharsets.UTF_8).size > MAX_NAME_BYTES }) return null
        return segments.joinToString("/")
    }

    private fun isRootDirectory(entry: String, directory: Boolean): Boolean =
        directory && !entry.startsWith('/') && entry.split('/').all { it.isEmpty() || it == "." }

    private fun isExcluded(path: String, pathPrefixes: Set<String>, namePrefixes: Set<String>): Boolean {
        if (pathPrefixes.any { prefix -> path == prefix || path.startsWith("$prefix/") }) return true
        return path.split('/').any { name -> namePrefixes.any(name::startsWith) }
    }

    private fun ancestors(path: String): Sequence<String> = sequence {
        var separator = path.indexOf('/')
        while (separator >= 0) {
            yield(path.substring(0, separator))
            separator = path.indexOf('/', separator + 1)
        }
    }

    private fun isValidLinkTarget(target: String): Boolean =
        target.isNotEmpty() && '\u0000' !in target && target.toByteArray(StandardCharsets.UTF_8).size <= MAX_PATH_BYTES

    private const val MAX_PATH_BYTES = 4095
    private const val MAX_NAME_BYTES = 255
    private val REGULAR_FILE_TYPES = setOf(
        TarConstants.LF_OLDNORM,
        TarConstants.LF_NORMAL,
        TarConstants.LF_CONTIG,
        TarConstants.LF_GNUTYPE_SPARSE,
    )
}
