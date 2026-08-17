package com.moonforce.ohmyainote.ink

import android.graphics.Color
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.moonforce.ohmyainote.document.model.StockBrush
import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.StrokePoint
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.StrokeTool
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class FinishedStroke(val record: StrokeRecord, val ink: Stroke)

object StrokeBridge {
    private const val PT_TO_CM = 2.54f / 72f

    fun toInk(record: StrokeRecord): Stroke {
        require(record.points.isNotEmpty())
        val hasPressure = record.points.all { it.pressure != null }
        val hasTilt = record.points.all { it.tiltRadians != null }
        val hasOrientation = record.points.all { it.orientationRadians != null }
        val inputs = MutableStrokeInputBatch()
        record.points.forEach { point ->
            inputs.add(
                type = InputToolType.STYLUS,
                x = point.x,
                y = point.y,
                elapsedTimeMillis = point.tMs,
                strokeUnitLengthCm = PT_TO_CM,
                pressure = if (hasPressure) point.pressure!! else StrokeInput.NO_PRESSURE,
                tiltRadians = if (hasTilt) point.tiltRadians!! else StrokeInput.NO_TILT,
                orientationRadians = if (hasOrientation) point.orientationRadians!! else StrokeInput.NO_ORIENTATION,
            )
        }
        val family = when (record.stockBrush) {
            StockBrush.PRESSURE_PEN -> StockBrushes.pressurePen()
            StockBrush.HIGHLIGHTER -> StockBrushes.highlighter()
        }
        val brush = Brush.createWithColorIntArgb(
            family,
            Color.parseColor(record.color),
            record.sizePt,
            record.epsilon,
        )
        return Stroke(brush, inputs)
    }

    fun fromInk(
        stroke: Stroke,
        tool: Tool,
        colorArgb: Int,
        id: String = UUID.randomUUID().toString(),
        startedAt: String = Instant.now().toString(),
    ): StrokeRecord {
        require(tool.isWriting)
        val points = buildList(stroke.inputs.size) {
            repeat(stroke.inputs.size) { index ->
                val input = stroke.inputs[index]
                add(
                    StrokePoint(
                        x = input.x,
                        y = input.y,
                        tMs = input.elapsedTimeMillis,
                        pressure = if (input.hasPressure) input.pressure else null,
                        tiltRadians = if (input.hasTilt) input.tiltRadians else null,
                        orientationRadians = if (input.hasOrientation) input.orientationRadians else null,
                    )
                )
            }
        }
        return StrokeRecord(
            id = id,
            tool = if (tool == Tool.HIGHLIGHTER) StrokeTool.HIGHLIGHTER else StrokeTool.PEN,
            stockBrush = if (tool == Tool.HIGHLIGHTER) StockBrush.HIGHLIGHTER else StockBrush.PRESSURE_PEN,
            color = "#%08X".format(colorArgb),
            sizePt = stroke.brush.size,
            epsilon = stroke.brush.epsilon,
            t0 = startedAt,
            pointCount = points.size,
            aabb = points.bounds(),
            points = points,
        )
    }

    fun load(records: List<StrokeRecord>): List<FinishedStroke> = records.map { FinishedStroke(it, toInk(it)) }

    private fun List<StrokePoint>.bounds(): PageRect {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        forEach { point ->
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        return PageRect(left, top, right, bottom)
    }
}
