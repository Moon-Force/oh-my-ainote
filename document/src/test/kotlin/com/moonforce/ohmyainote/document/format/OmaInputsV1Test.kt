package com.moonforce.ohmyainote.document.format

import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.StockBrush
import com.moonforce.ohmyainote.document.model.StrokePoint
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.StrokeTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OmaInputsV1Test {
    @Test
    fun variablePointFlagsRoundTrip() {
        val stroke = StrokeRecord(
            id = "stroke-1",
            tool = StrokeTool.PEN,
            stockBrush = StockBrush.PRESSURE_PEN,
            color = "#FF1A1A1A",
            sizePt = 2.5f,
            t0 = "2026-08-18T00:00:00Z",
            points = listOf(
                StrokePoint(1f, 2f, 0),
                StrokePoint(3f, 4f, 8, pressure = 0.7f),
                StrokePoint(5f, 6f, 16, pressure = 0.8f, tiltRadians = 0.2f, orientationRadians = 1.1f),
            ),
        )

        val encoded = OmaInputsV1.encode(listOf(stroke))
        val decoded = OmaInputsV1.decode(encoded.bytes, encoded.records).single()

        assertEquals(3, decoded.pointCount)
        assertEquals(0.7f, decoded.points[1].pressure!!, 0f)
        assertNull(decoded.points[1].tiltRadians)
        assertEquals(1.1f, decoded.points[2].orientationRadians!!, 0f)
        assertEquals(PageRect(1f, 2f, 5f, 6f), decoded.aabb)
    }
}
