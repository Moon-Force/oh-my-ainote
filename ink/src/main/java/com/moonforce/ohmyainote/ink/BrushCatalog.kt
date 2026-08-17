package com.moonforce.ohmyainote.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes

object BrushCatalog {
    const val PEN_SIZE_PT = 2.5f
    const val HIGHLIGHTER_SIZE_PT = 14f
    const val EPSILON_PT = 0.01f

    fun create(tool: Tool, colorArgb: Int = defaultColor(tool)): Brush? = when (tool) {
        Tool.PEN -> Brush.createWithColorIntArgb(
            StockBrushes.pressurePen(),
            colorArgb,
            PEN_SIZE_PT,
            EPSILON_PT,
        )
        Tool.HIGHLIGHTER -> Brush.createWithColorIntArgb(
            StockBrushes.highlighter(),
            colorArgb,
            HIGHLIGHTER_SIZE_PT,
            EPSILON_PT,
        )
        else -> null
    }

    fun defaultColor(tool: Tool): Int = when (tool) {
        Tool.HIGHLIGHTER -> 0x66FFEB3BL.toInt()
        else -> 0xFF1A1A1AL.toInt()
    }
}
