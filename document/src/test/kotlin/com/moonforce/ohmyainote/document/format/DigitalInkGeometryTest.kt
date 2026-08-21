package com.moonforce.ohmyainote.document.format

import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.StockBrush
import com.moonforce.ohmyainote.document.model.StrokePoint
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.StrokeTool
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalInkGeometryTest {
    @Test
    fun samplesTranslateToAreaOriginAndUseAbsoluteTimestamps() {
        val start = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val sample = DigitalInkGeometry.samples(
            listOf(
                stroke(
                    id = "a",
                    t0 = "2026-01-01T00:00:00Z",
                    aabb = PageRect(10f, 20f, 40f, 50f),
                    points = listOf(StrokePoint(10f, 20f, 0), StrokePoint(40f, 50f, 16)),
                ),
            ),
        )
        assertEquals(10f, sample.area.l)
        assertEquals(20f, sample.area.t)
        assertEquals(0f, sample.strokes.single().points[0].x)
        assertEquals(0f, sample.strokes.single().points[0].y)
        assertEquals(30f, sample.strokes.single().points[1].x)
        assertEquals(30f, sample.strokes.single().points[1].y)
        assertEquals(start, sample.strokes.single().points[0].tMs)
        assertEquals(start + 16, sample.strokes.single().points[1].tMs)
    }

    @Test
    fun placementUsesClusterAabbAndFirstPenColor() {
        val record = DigitalInkGeometry.placement(
            records = listOf(
                stroke(
                    id = "a",
                    color = "#FF112233",
                    aabb = PageRect(8f, 12f, 80f, 40f),
                    points = listOf(StrokePoint(8f, 12f, 0), StrokePoint(80f, 40f, 10)),
                ),
            ),
            text = "你好",
            id = "text-1",
            createdAt = "2026-01-01T00:00:00Z",
        )
        assertEquals("你好", record.text)
        assertEquals(8f, record.x)
        assertEquals(12f, record.y)
        assertEquals(28f, record.fontSizePt)
        assertEquals("#FF112233", record.color)
        assertEquals(8f, record.aabb.l)
        assertEquals(12f, record.aabb.t)
        assertTrue(record.aabb.width >= 28f * 2f)
    }

    @Test
    fun penStrokesDropHighlighters() {
        val pen = stroke("pen", tool = StrokeTool.PEN)
        val highlighter = stroke(
            "mark",
            tool = StrokeTool.HIGHLIGHTER,
            stockBrush = StockBrush.HIGHLIGHTER,
        )
        assertEquals(listOf("pen"), DigitalInkGeometry.penStrokes(listOf(pen, highlighter)).map { it.id })
    }

    private fun stroke(
        id: String,
        tool: StrokeTool = StrokeTool.PEN,
        stockBrush: StockBrush = StockBrush.OMA_PRESSURE_INK_V1,
        color: String = "#FF000000",
        t0: String = "2026-01-01T00:00:00Z",
        aabb: PageRect = PageRect(0f, 0f, 10f, 10f),
        points: List<StrokePoint> = listOf(StrokePoint(0f, 0f, 0), StrokePoint(10f, 10f, 8)),
    ) = StrokeRecord(
        id = id,
        tool = tool,
        stockBrush = stockBrush,
        color = color,
        sizePt = 2.5f,
        t0 = t0,
        aabb = aabb,
        pointCount = points.size,
        points = points,
    )
}
