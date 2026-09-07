package com.xayah.core.util.command

import com.xayah.core.util.model.ShellResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TarUtilTest {
    @Test
    fun acceptsOnlyFileChangedWarnings() {
        val changed = ShellResult(
            code = 1,
            input = emptyList(),
            out = listOf(
                "tar: app/database.db: file changed as we read it",
                "Total bytes written: 10240 (10KiB)",
            ),
        )
        val unreadable = ShellResult(
            code = 1,
            input = emptyList(),
            out = listOf("tar: app/database.db: Permission denied"),
        )
        val mixed = ShellResult(
            code = 1,
            input = emptyList(),
            out = changed.out + unreadable.out,
        )

        assertTrue(changed.acceptFileChangedWarnings().isSuccess)
        assertFalse(unreadable.acceptFileChangedWarnings().isSuccess)
        assertFalse(mixed.acceptFileChangedWarnings().isSuccess)
    }
}
