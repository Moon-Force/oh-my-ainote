package com.moonforce.ohmyainote.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushBehavior
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.behavior.DampingNode
import androidx.ink.brush.behavior.OutOfRange
import androidx.ink.brush.behavior.ProgressDomain
import androidx.ink.brush.behavior.SourceNode
import androidx.ink.brush.behavior.TargetNode
import androidx.ink.brush.behavior.ToolTypeFilterNode
import com.moonforce.ohmyainote.document.model.OmaPressureInkV1

object BrushCatalog {
    const val PEN_SIZE_PT = 2.5f
    const val HIGHLIGHTER_SIZE_PT = 14f
    const val EPSILON_PT = 0.01f

    private val pressureInkFamily: BrushFamily by lazy(::buildPressureInkFamily)

    fun create(
        tool: Tool,
        colorArgb: Int = defaultColor(tool),
        sizePt: Float = defaultSize(tool),
    ): Brush? = when (tool) {
        Tool.PEN -> Brush.createWithColorIntArgb(
            pressureInkFamily(),
            colorArgb,
            sizePt,
            EPSILON_PT,
        )
        Tool.HIGHLIGHTER -> Brush.createWithColorIntArgb(
            StockBrushes.highlighter(),
            colorArgb,
            sizePt,
            EPSILON_PT,
        )
        else -> null
    }

    fun pressureInkFamily(): BrushFamily = pressureInkFamily

    @OptIn(ExperimentalInkCustomBrushApi::class)
    private fun buildPressureInkFamily(): BrushFamily {
        val base = StockBrushes.pressurePen()
        val coat = base.coats.single()
        val pressureBehaviors = listOf(
            stylusPressureBehavior(
                target = TargetNode.Target.SIZE_MULTIPLIER,
                sourceEnd = OmaPressureInkV1.LOW_PRESSURE_END,
                targetStart = OmaPressureInkV1.MIN_WIDTH_MULTIPLIER,
                targetEnd = 1f,
                comment = "Low pressure narrows the official pressure pen.",
            ),
            stylusPressureBehavior(
                target = TargetNode.Target.OPACITY_MULTIPLIER,
                sourceEnd = 1f,
                targetStart = OmaPressureInkV1.MIN_OPACITY_MULTIPLIER,
                targetEnd = 1f,
                comment = "Stylus pressure controls ink darkness.",
            ),
        )
        return base.copy(
            coat = coat.copy(tip = coat.tip.copy(behaviors = coat.tip.behaviors + pressureBehaviors)),
            developerComment = "Official pressure pen plus damped stylus width and opacity.",
            clientBrushFamilyId = "oma_pressure_ink_v1",
        )
    }

    private fun stylusPressureBehavior(
        target: TargetNode.Target,
        sourceEnd: Float,
        targetStart: Float,
        targetEnd: Float,
        comment: String,
    ): BrushBehavior = BrushBehavior(
        TargetNode(
            target,
            targetStart,
            targetEnd,
            ToolTypeFilterNode(
                setOf(InputToolType.STYLUS),
                DampingNode(
                    ProgressDomain.TIME_IN_SECONDS,
                    OmaPressureInkV1.DAMPING_GAP_SECONDS,
                    SourceNode(
                        SourceNode.Source.NORMALIZED_PRESSURE,
                        0f,
                        sourceEnd,
                        OutOfRange.CLAMP,
                    ),
                ),
            ),
        ),
        comment,
    )

    fun defaultColor(tool: Tool): Int = when (tool) {
        Tool.HIGHLIGHTER -> 0x66FFEB3BL.toInt()
        else -> 0xFF1A1A1AL.toInt()
    }

    fun defaultSize(tool: Tool): Float = when (tool) {
        Tool.HIGHLIGHTER -> HIGHLIGHTER_SIZE_PT
        else -> PEN_SIZE_PT
    }
}
