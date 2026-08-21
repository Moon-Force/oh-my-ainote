package com.moonforce.ohmyainote.document.format

import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.StrokeTool
import com.moonforce.ohmyainote.document.model.TextRecord
import java.time.Instant
import kotlin.math.max

data class InkSamplePoint(val x: Float, val y: Float, val tMs: Long)

data class InkSampleStroke(val points: List<InkSamplePoint>)

data class InkSample(val area: PageRect, val strokes: List<InkSampleStroke>)

object DigitalInkGeometry {
    const val MIN_FONT_SIZE_PT = 8f

    fun penStrokes(records: List<StrokeRecord>): List<StrokeRecord> =
        records.filter { it.tool == StrokeTool.PEN && it.points.isNotEmpty() }

    fun samples(records: List<StrokeRecord>): InkSample {
        val pens = penStrokes(records)
        require(pens.isNotEmpty()) { "No pen strokes to recognize" }
        val area = PageRect.union(pens.map { it.aabb })
        val strokes = pens.map { record ->
            val origin = Instant.parse(record.t0).toEpochMilli()
            InkSampleStroke(
                record.points.map { point ->
                    InkSamplePoint(
                        x = point.x - area.l,
                        y = point.y - area.t,
                        tMs = origin + point.tMs,
                    )
                },
            )
        }
        return InkSample(area, strokes)
    }

    fun placement(
        records: List<StrokeRecord>,
        text: String,
        id: String,
        createdAt: String,
    ): TextRecord {
        require(text.isNotBlank()) { "Recognized text is blank" }
        val pens = penStrokes(records)
        require(pens.isNotEmpty())
        val cluster = PageRect.union(pens.map { it.aabb })
        val fontSize = max(MIN_FONT_SIZE_PT, cluster.height)
        val width = max(cluster.width, estimatedWidth(text, fontSize))
        val height = max(cluster.height, fontSize)
        return TextRecord(
            id = id,
            text = text.trim(),
            x = cluster.l,
            y = cluster.t,
            fontSizePt = fontSize,
            color = pens.first().color,
            aabb = PageRect(cluster.l, cluster.t, cluster.l + width, cluster.t + height),
            createdAt = createdAt,
        )
    }

    fun estimatedWidth(text: String, fontSizePt: Float): Float {
        var width = 0f
        for (character in text) {
            width += if (character.code > 0xFF) fontSizePt else fontSizePt * 0.55f
        }
        return width
    }
}
