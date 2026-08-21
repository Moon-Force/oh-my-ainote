package com.moonforce.ohmyainote.ui.editor

import androidx.compose.ui.unit.IntSize
import com.moonforce.ohmyainote.ink.ViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilePlanTest {

    @Test
    fun planCoversVisibleRectPlusOneTileMargin() {
        // 160dpi, scale 1, bucket 1.25: pixelsPerPoint = (160/72)*1.25 ≈ 2.778, tilePt ≈ 184.3
        val viewport = ViewportState(scale = 1f, densityDpi = 160f)
        val viewSize = IntSize(1024, 1024)
        val plan = visibleTilePlan(viewport, viewSize, pageWidthPt = 595f, pageHeightPt = 842f, scaleBucket = 1.25f, pageIndex = 0)
        assertTrue(plan.tilePt > 0f)
        val visibleWidthPt = 1024f / (160f / 72f * 1.25f)
        val tilesSpan = (visibleWidthPt / plan.tilePt).toInt() + 1
        assertTrue("x range ${plan.x1 - plan.x0} should span >= ${tilesSpan - 1}", plan.x1 - plan.x0 >= tilesSpan - 1)
        assertTrue(plan.y1 - plan.y0 >= tilesSpan - 1)
        assertTrue(plan.x0 >= 0)
        assertTrue(plan.y0 >= 0)
        assertEquals(0, plan.pageIndex)
    }

    @Test
    fun planClampsToPageWhenPannedFarOutside() {
        val viewport = ViewportState(scale = 1f, panX = 5000f, panY = 5000f, densityDpi = 160f)
        val plan = visibleTilePlan(viewport, IntSize(100, 100), 595f, 842f, 1.25f, 7)
        assertTrue(plan.x0 >= 0)
        assertTrue(plan.y0 >= 0)
        assertTrue(plan.x1 >= plan.x0)
        assertTrue(plan.y1 >= plan.y0)
        assertEquals(7, plan.pageIndex)
    }

    @Test
    fun planCoversWholePageAtHighZoomOut() {
        val viewport = ViewportState(scale = 0.2f, panX = 0f, panY = 0f, densityDpi = 160f)
        val plan = visibleTilePlan(viewport, IntSize(400, 600), 595f, 842f, 1.25f, 3)
        // At 0.2x zoom the whole page is visible; margin tiles should cover all of it.
        assertTrue(plan.x1 * plan.tilePt >= 595f - plan.tilePt)
        assertTrue(plan.y1 * plan.tilePt >= 842f - plan.tilePt)
    }

    @Test
    fun tileSizeScalesWithDensityAndZoomBucket() {
        val low = visibleTilePlan(ViewportState(densityDpi = 160f), IntSize(800, 800), 595f, 842f, 1.25f, 0)
        val high = visibleTilePlan(ViewportState(densityDpi = 320f), IntSize(800, 800), 595f, 842f, 1.25f, 0)
        assertTrue("tilePt should halve when density doubles: ${low.tilePt} vs ${high.tilePt}",
            high.tilePt < low.tilePt)
        assertTrue(high.tilePt > low.tilePt / 2.5f)
    }
}
