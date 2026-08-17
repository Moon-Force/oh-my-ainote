package com.moonforce.ohmyainote.document.format

import com.moonforce.ohmyainote.document.model.PagePoint
import com.moonforce.ohmyainote.document.model.PdfRect
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    @Test
    fun allPdfRotationCornersMatchFormatTable() {
        val crop = PdfRect(l = 10f, b = 20f, r = 110f, t = 220f)
        val cases = mapOf(
            0 to listOf(
                PagePoint(0f, 0f) to PagePoint(10f, 220f),
                PagePoint(100f, 0f) to PagePoint(110f, 220f),
                PagePoint(0f, 200f) to PagePoint(10f, 20f),
                PagePoint(100f, 200f) to PagePoint(110f, 20f),
            ),
            90 to listOf(
                PagePoint(0f, 0f) to PagePoint(10f, 20f),
                PagePoint(200f, 0f) to PagePoint(10f, 220f),
                PagePoint(0f, 100f) to PagePoint(110f, 20f),
                PagePoint(200f, 100f) to PagePoint(110f, 220f),
            ),
            180 to listOf(
                PagePoint(0f, 0f) to PagePoint(110f, 20f),
                PagePoint(100f, 0f) to PagePoint(10f, 20f),
                PagePoint(0f, 200f) to PagePoint(110f, 220f),
                PagePoint(100f, 200f) to PagePoint(10f, 220f),
            ),
            270 to listOf(
                PagePoint(0f, 0f) to PagePoint(110f, 220f),
                PagePoint(200f, 0f) to PagePoint(110f, 20f),
                PagePoint(0f, 100f) to PagePoint(10f, 220f),
                PagePoint(200f, 100f) to PagePoint(10f, 20f),
            ),
        )

        cases.forEach { (rotate, points) ->
            points.forEach { (page, expected) ->
                assertEquals("rotate=$rotate point=$page", expected, pageToPdfUserSpace(page, crop, rotate))
            }
        }
    }

    @Test
    fun pageViewMatrixRoundTripsWithDensityOriginPanAndZoom() {
        listOf(2.0, 3.5).forEach { density ->
            val matrix = Affine2.pageToView(
                densityDpi = density * 160.0,
                scale = 4.25,
                pageOriginX = 123.0,
                pageOriginY = 47.0,
                panX = -91.0,
                panY = 33.0,
            )
            val input = PagePoint(419.25f, 72.5f)
            val roundTrip = matrix.invert().transform(matrix.transform(input))
            assertEquals(input.x, roundTrip.x, 1e-3f)
            assertEquals(input.y, roundTrip.y, 1e-3f)
        }
    }
}
