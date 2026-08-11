package com.xayah.core.model

data class ColoredLabel(val label: String, val colorArgb: Long)

object LabelPalette {
    val colors = listOf(
        0xFFD32F2FL,
        0xFFF57C00L,
        0xFFF9A825L,
        0xFF388E3CL,
        0xFF00897BL,
        0xFF1976D2L,
        0xFF7B1FA2L,
        0xFFC2185BL,
    )

    fun default(label: String): Long = colors[Math.floorMod(label.hashCode(), colors.size)]
}
