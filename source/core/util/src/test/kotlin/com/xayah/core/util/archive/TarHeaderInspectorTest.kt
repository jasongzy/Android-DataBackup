package com.xayah.core.util.archive

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TarHeaderInspectorTest {
    @Test
    fun preservesSupportedEntryNames() {
        val longName = "pkg/${"directory/".repeat(15)}file.txt"
        val archive = archive(
            Entry("./", TarConstants.LF_DIR),
            Entry("pkg/", TarConstants.LF_DIR),
            Entry("pkg/name with spaces.txt", data = "spaces".toByteArray()),
            Entry("pkg/line\nbreak.txt", data = "newline".toByteArray()),
            Entry(longName, data = "long".toByteArray()),
        )
        val approved = mutableListOf<String>()

        val result = TarHeaderInspector.inspect(ByteArrayInputStream(archive), requiredPrefix = "pkg", onApprovedEntry = approved::add)

        assertEquals(listOf("./", "pkg/", "pkg/name with spaces.txt", "pkg/line\nbreak.txt", longName), approved)
        assertEquals(emptyList<TarHeaderInspector.Link>(), result.links)
        assertEquals(0, result.skippedEntries)
    }

    @Test
    fun rejectsPathsOutsideRequiredRoot() {
        val traversal = archive(Entry("../outside", data = byteArrayOf(1)))
        val wrongRoot = archive(Entry("other/file", data = byteArrayOf(1)))

        assertThrows(IllegalStateException::class.java) {
            TarHeaderInspector.inspect(ByteArrayInputStream(traversal), requiredPrefix = "pkg") {}
        }
        assertThrows(IllegalStateException::class.java) {
            TarHeaderInspector.inspect(ByteArrayInputStream(wrongRoot), requiredPrefix = "pkg") {}
        }
    }

    @Test
    fun appliesRestoreExclusionsWithoutDroppingOtherEntries() {
        val archive = archive(
            Entry("pkg/cache/item", data = byteArrayOf(1)),
            Entry("pkg/files/Backup_old/value", data = byteArrayOf(1)),
            Entry("pkg/files/kept", data = byteArrayOf(1)),
        )
        val approved = mutableListOf<String>()

        TarHeaderInspector.inspect(
            input = ByteArrayInputStream(archive),
            requiredPrefix = "pkg",
            excludedPathPrefixes = setOf("pkg/cache"),
            excludedNamePrefixes = setOf("Backup_"),
            onApprovedEntry = approved::add,
        )

        assertEquals(listOf("pkg/files/kept"), approved)
    }

    @Test
    fun recordsLiteralLinksAndSkipsNestedLinkDestinations() {
        val archive = archive(
            Entry("pkg/external", TarConstants.LF_SYMLINK, target = "/outside/target"),
            Entry("pkg/external/nested", TarConstants.LF_SYMLINK, target = "relative"),
            Entry("pkg/hard", TarConstants.LF_LINK, target = "/existing/file"),
            Entry("pkg/fifo", TarConstants.LF_FIFO),
        )

        val result = TarHeaderInspector.inspect(ByteArrayInputStream(archive), requiredPrefix = "pkg") {}

        assertEquals(
            listOf(
                TarHeaderInspector.Link(TarHeaderInspector.LinkType.SYMBOLIC, "pkg/external", "/outside/target"),
                TarHeaderInspector.Link(TarHeaderInspector.LinkType.HARD, "pkg/hard", "/existing/file"),
            ),
            result.links,
        )
        assertEquals(2, result.skippedEntries)
    }

    private fun archive(vararg entries: Entry): ByteArray = ByteArrayOutputStream().use { bytes ->
        TarArchiveOutputStream(bytes).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setAddPaxHeadersForNonAsciiNames(true)
            entries.forEach { item ->
                val entry = TarArchiveEntry(item.name, item.type).apply {
                    linkName = item.target
                    size = item.data.size.toLong()
                }
                tar.putArchiveEntry(entry)
                tar.write(item.data)
                tar.closeArchiveEntry()
            }
        }
        bytes.toByteArray()
    }

    private data class Entry(
        val name: String,
        val type: Byte = TarConstants.LF_NORMAL,
        val target: String = "",
        val data: ByteArray = byteArrayOf(),
    )
}
