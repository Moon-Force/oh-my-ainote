package com.moonforce.ohmyainote.export

import com.moonforce.ohmyainote.document.format.Affine2
import com.moonforce.ohmyainote.document.format.pageToPdfUserSpace
import com.moonforce.ohmyainote.document.model.PagePoint
import com.moonforce.ohmyainote.document.model.PdfRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportGeometryTest {

    private val a4Crop = PdfRect(l = 0f, b = 0f, r = 595f, t = 842f)

    @Test
    fun pageOriginMapsToCropOriginForRotateZero() {
        assertEquals(PagePoint(0f, 842f), pageToPdfUserSpace(PagePoint(0f, 0f), a4Crop, 0))
    }

    @Test
    fun bottomRightMapsToPdfBottomRightForRotateZero() {
        val p = pageToPdfUserSpace(PagePoint(595f, 842f), a4Crop, 0)
        assertEquals(PagePoint(595f, 0f), p)
    }

    @Test
    fun rotate90SwapsAxesPerFormatTable() {
        // Display space after /Rotate 90: display width = crop height.
        val p = pageToPdfUserSpace(PagePoint(0f, 0f), a4Crop, 90)
        assertEquals(PagePoint(0f, 0f), p)
        val p2 = pageToPdfUserSpace(PagePoint(842f, 0f), a4Crop, 90)
        assertEquals(PagePoint(0f, 842f), p2)
    }

    @Test
    fun rotate180MirrorsBothAxes() {
        assertEquals(PagePoint(595f, 0f), pageToPdfUserSpace(PagePoint(0f, 0f), a4Crop, 180))
        assertEquals(PagePoint(0f, 842f), pageToPdfUserSpace(PagePoint(595f, 842f), a4Crop, 180))
    }

    @Test
    fun rotate270MatchesFormatTable() {
        assertEquals(PagePoint(595f, 842f), pageToPdfUserSpace(PagePoint(0f, 0f), a4Crop, 270))
    }

    @Test
    fun invalidRotationRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            pageToPdfUserSpace(PagePoint(0f, 0f), a4Crop, 45)
        }
    }

    @Test
    fun zeroSizeCropRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            pageToPdfUserSpace(PagePoint(0f, 0f), PdfRect(0f, 0f, 0f, 0f), 0)
        }
    }

    @Test
    fun exportOptionsDefaultsMatchFrozenBehavior() {
        val options = ExportOptions()
        assertTrue(options.pressureVarying)
        assertEquals("oh-my-ainote", options.producer)
    }

    @Test
    fun exportProgressReceivesMonotonicPageCounts() {
        val seen = mutableListOf<Pair<Int, Int>>()
        val progress = ExportProgress { completed, total -> seen += completed to total }
        progress.onPage(1, 3)
        progress.onPage(2, 3)
        progress.onPage(3, 3)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), seen)
    }
}
