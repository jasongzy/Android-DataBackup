package com.xayah.core.util.command

import com.xayah.core.common.util.toSpaceString
import com.xayah.core.common.util.trim
import com.xayah.core.util.SymbolUtil
import com.xayah.core.util.model.ShellResult

object Tar {
    data class ExtractionResult(
        val result: ShellResult,
        val skippedEntries: Int,
        val pendingLinks: Int,
    )

    private suspend fun execute(vararg args: String): ShellResult = BaseUtil.execute("tar", *args)

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    suspend fun compressInCur(cur: String, src: String, dst: String, extra: String): ShellResult {
        val command = mutableListOf("cd", shellQuote(cur), "&&", "tar", "--totals", "-cpf", "-", src)
        if (extra.isNotEmpty()) command += listOf("|", extra)
        command += listOf(">", shellQuote(dst))
        return BaseUtil.execute(*command.toTypedArray())
    }

    suspend fun compress(exclusionList: List<String>, h: String, srcDir: String, src: String, dst: String, extra: String): ShellResult =
        run {
            val exclusion = exclusionList.trim().map { "--exclude=$it" }.toSpaceString()
            if (extra.isEmpty()) {
                // tar --totals "$exclusion" $h -cpf - -C "$srcDir" -- "$src" > "$dst"
                execute(
                    "--totals",
                    exclusion,
                    h,
                    "-cpf",
                    "-",
                    "-C",
                    shellQuote(srcDir),
                    "--",
                    shellQuote(src),
                    ">",
                    shellQuote(dst),
                )
            } else {
                // tar --totals "$exclusion" $h -cpf - -C "$srcDir" -- "$src" | $extra > "$dst"
                execute(
                    "--totals",
                    exclusion,
                    h,
                    "-cpf",
                    "-",
                    "-C",
                    shellQuote(srcDir),
                    "--",
                    shellQuote(src),
                    "|",
                    extra,
                    ">",
                    shellQuote(dst),
                )
            }
        }

    suspend fun test(src: String, extra: String): ShellResult = if (extra.isEmpty()) {
        // tar -tf "$src" > /dev/null 2>&1
        execute(
            "-tf",
            shellQuote(src),
            ">",
            "/dev/null",
            "2>&1",
        )
    } else {
        // zstd -d -c "$src" | tar -tf - > /dev/null 2>&1
        BaseUtil.execute(
            "zstd",
            "-d",
            "-c",
            shellQuote(src),
            "|",
            "tar",
            "-tf",
            "-",
            ">",
            "/dev/null",
            "2>&1",
        )
    }

    suspend fun hasContent(src: String, rootEntry: String, extra: String): ShellResult {
        val filter = "awk -v root=${shellQuote(rootEntry)} " +
            "'${SymbolUtil.USD}0 != root && ${SymbolUtil.USD}0 != root \"/\" { found=1 } END { exit !found }'"
        return if (extra.isEmpty()) {
            execute("-tf", shellQuote(src), "|", filter)
        } else {
            BaseUtil.execute(
                "zstd",
                "-d",
                "-c",
                shellQuote(src),
                "|",
                "tar",
                "-tf",
                "-",
                "|",
                filter,
            )
        }
    }

    suspend fun decompress(src: String, dst: String, extra: String): ShellResult =
        decompressSafely(src = src, dst = dst, extra = extra).result

    suspend fun decompressGzipSafely(src: String, dst: String, linkDir: String): ExtractionResult =
        decompressSafely(
            src = src,
            dst = dst,
            extra = "gzip",
            metadataOptions = "--no-same-owner --no-same-permissions",
            preservePermissions = false,
            linkDir = linkDir,
        )

    suspend fun decompressWithLinks(
        exclusionList: List<String>,
        clear: String,
        m: Boolean,
        src: String,
        dst: String,
        extra: String,
        linkDir: String,
        requiredPrefix: String,
    ): ExtractionResult {
        require(requiredPrefix.isNotEmpty() && requiredPrefix.none { it == '/' || it == '\\' || it == '\u0000' || it == '\n' || it == '\r' })
        val exclusion = exclusionList.trim().map { "--exclude=$it" }.toSpaceString()
        return decompressSafely(
            src = src,
            dst = dst,
            extra = extra,
            exclusion = exclusion,
            clear = clear,
            ignoreModificationTime = m,
            linkDir = linkDir,
            requiredPrefix = requiredPrefix,
        )
    }

    suspend fun decompress(exclusionList: List<String>, clear: String, m: Boolean, src: String, dst: String, extra: String): ShellResult = run {
        val exclusion = exclusionList.trim().map { "--exclude=$it" }.toSpaceString()
        decompressSafely(
            src = src,
            dst = dst,
            extra = extra,
            exclusion = exclusion,
            clear = clear,
            ignoreModificationTime = m,
        ).result
    }

    private suspend fun decompressSafely(
        src: String,
        dst: String,
        extra: String,
        exclusion: String = "",
        clear: String = "",
        ignoreModificationTime: Boolean = true,
        metadataOptions: String = "",
        preservePermissions: Boolean = true,
        linkDir: String? = null,
        requiredPrefix: String = "",
    ): ExtractionResult {
        val quotedSrc = shellQuote(src)
        val quotedDst = shellQuote(dst)
        val input = when (extra) {
            "" -> "busybox cat $quotedSrc"
            "gzip" -> "busybox gzip -dc $quotedSrc"
            else -> "$extra -d -c $quotedSrc"
        }
        val flags = buildString {
            append("-x")
            if (ignoreModificationTime) append('m')
            if (preservePermissions) append('p')
            append('f')
        }
        val skippedMarker = "SKIPPED_ARCHIVE_ENTRIES="
        val pendingMarker = "PENDING_ARCHIVE_LINKS="
        val quotedPrefix = shellQuote(requiredPrefix)
        val linkSetup = linkDir?.let { directory ->
            val quotedLinkDir = shellQuote(directory)
            """
                busybox mkdir -p $quotedLinkDir/symlinks || exit 1
                busybox cp "${SymbolUtil.USD}hardlinks" $quotedLinkDir/hardlinks || exit 1
                if [ -s "${SymbolUtil.USD}safe_symlinks" ]; then
                    $input | tar --no-recursion --verbatim-files-from --no-unquote --no-same-owner --no-same-permissions -xf - -C $quotedLinkDir/symlinks -T "${SymbolUtil.USD}safe_symlinks" || exit 1
                fi
                pending=${SymbolUtil.USD}((safe_symlink_count + hardlink_count))
                skipped=${SymbolUtil.USD}((special_count + symlink_count - safe_symlink_count))
            """.trimIndent()
        } ?: """
            pending=0
            skipped=${SymbolUtil.USD}(awk 'END { print NR+0 }' "${SymbolUtil.USD}excluded") || exit 1
        """.trimIndent()
        val workspaceRoot = linkDir?.substringBeforeLast('/') ?: dst
        val quotedWorkspaceRoot = shellQuote(workspaceRoot)
        val script = """
            workspace=${SymbolUtil.USD}(busybox mktemp -d $quotedWorkspaceRoot/.archive.XXXXXX) || exit 1
            [ -n "${SymbolUtil.USD}workspace" ] || exit 1
            trap 'busybox rm -rf -- "${SymbolUtil.USD}workspace"' EXIT
            entries="${SymbolUtil.USD}workspace/entries"
            all_entries="${SymbolUtil.USD}workspace/all_entries"
            details="${SymbolUtil.USD}workspace/details"
            excluded="${SymbolUtil.USD}workspace/excluded"
            symlinks="${SymbolUtil.USD}workspace/symlinks"
            safe_symlinks="${SymbolUtil.USD}workspace/safe_symlinks"
            hardlinks="${SymbolUtil.USD}workspace/hardlinks"
            special="${SymbolUtil.USD}workspace/special"
            $input | tar -tf - > "${SymbolUtil.USD}all_entries" || exit 1
            awk -v prefix=$quotedPrefix 'BEGIN { unsafe=0 } {
                if (substr(${SymbolUtil.USD}0,1,1)=="/") unsafe=1
                n=split(${SymbolUtil.USD}0,p,"/"); first=""
                for(i=1;i<=n;i++) {
                    if(p[i]=="..") unsafe=1
                    if(first=="" && p[i]!="" && p[i]!=".") first=p[i]
                }
                if(prefix!="" && first!=prefix) unsafe=1
            } END { exit unsafe }' "${SymbolUtil.USD}all_entries" || exit 1
            $input | tar $exclusion -tf - > "${SymbolUtil.USD}entries" || exit 1
            $input | tar $exclusion -tvf - > "${SymbolUtil.USD}details" || exit 1
            : > "${SymbolUtil.USD}excluded"; : > "${SymbolUtil.USD}symlinks"; : > "${SymbolUtil.USD}hardlinks"; : > "${SymbolUtil.USD}special"
            awk -v excluded="${SymbolUtil.USD}excluded" -v symlinks="${SymbolUtil.USD}symlinks" -v hardlinks="${SymbolUtil.USD}hardlinks" -v special="${SymbolUtil.USD}special" '
                NR==FNR { names[NR]=${SymbolUtil.USD}0; next }
                {
                    type=substr(${SymbolUtil.USD}1,1,1); name=names[FNR]
                    if(type=="-") next
                    if(type=="d") next
                    print name >> excluded
                    if(type=="l") { print name >> symlinks; next }
                    if(type=="h") {
                        marker=name " link to "; pos=index(${SymbolUtil.USD}0,marker)
                        if(pos>0) print name "\t" substr(${SymbolUtil.USD}0,pos+length(marker)) >> hardlinks
                        else print name >> special
                        next
                    }
                    print name >> special
                }
            ' "${SymbolUtil.USD}entries" "${SymbolUtil.USD}details" || exit 1
            awk '{ links[NR]=${SymbolUtil.USD}0 } END { for(i=1;i<=NR;i++) { safe=1; for(j=1;j<=NR;j++) if(i!=j && index(links[i],links[j] "/")==1) safe=0; if(safe) print links[i] } }' "${SymbolUtil.USD}symlinks" > "${SymbolUtil.USD}safe_symlinks" || exit 1
            symlink_count=${SymbolUtil.USD}(awk 'END { print NR+0 }' "${SymbolUtil.USD}symlinks") || exit 1
            safe_symlink_count=${SymbolUtil.USD}(awk 'END { print NR+0 }' "${SymbolUtil.USD}safe_symlinks") || exit 1
            hardlink_count=${SymbolUtil.USD}(awk 'END { print NR+0 }' "${SymbolUtil.USD}hardlinks") || exit 1
            special_count=${SymbolUtil.USD}(awk 'END { print NR+0 }' "${SymbolUtil.USD}special") || exit 1
            $input | tar --totals $exclusion $clear $metadataOptions --exclude-from="${SymbolUtil.USD}excluded" $flags - -C $quotedDst || exit 1
            $linkSetup
            echo "$skippedMarker${SymbolUtil.USD}skipped"
            echo "$pendingMarker${SymbolUtil.USD}pending"
        """.trimIndent()
        val result = BaseUtil.execute(script)
        val skippedEntries = result.out.firstNotNullOfOrNull { line ->
            line.substringAfter(skippedMarker, missingDelimiterValue = "").toIntOrNull()
        } ?: 0
        val pendingLinks = result.out.firstNotNullOfOrNull { line ->
            line.substringAfter(pendingMarker, missingDelimiterValue = "").toIntOrNull()
        } ?: 0
        return ExtractionResult(result, skippedEntries, pendingLinks)
    }
}
