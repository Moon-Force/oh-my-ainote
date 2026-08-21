package com.moonforce.ohmyainote.ink

import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.TextRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-runnable slice of [HitTest].
 *
 * `eraseIntersectingTexts` operates purely on [TextRecord] bounding boxes and needs no Android
 * runtime, so it is tested here. `eraseIntersectingStrokes` computes the live ink geometry via
 * `Stroke.shape`, which loads `androidx.ink`'s native `StrokeInputBatchNative` library — that
 * only resolves on-device, so the stroke path is covered by instrumented tests rather than in
 * this JVM suite.
 */
class HitTestTest {

    @Test
    fun eraseTextsMatchByAabbOverlapOnly() {
        val near = TextRecord("t1", "hi", 0f, 0f, 12f, "#FF000000", PageRect(0f, 0f, 30f, 20f), "2026-01-01T00:00:00Z")
        val far = TextRecord("t2", "bye", 500f, 500f, 12f, "#FF000000", PageRect(500f, 500f, 530f, 520f), "2026-01-01T00:00:00Z")
        val ids = eraseIntersectingTexts(10f, 10f, 20f, 20f, listOf(near, far))
        assertEquals(setOf("t1"), ids)
    }

    @Test
    fun eraseTextsIgnoreWhenSweepMissesAllBoxes() {
        val far = TextRecord("t1", "hi", 0f, 0f, 12f, "#FF000000", PageRect(100f, 100f, 130f, 120f), "2026-01-01T00:00:00Z")
        val ids = eraseIntersectingTexts(0f, 0f, 5f, 5f, listOf(far))
        assertTrue(ids.isEmpty())
    }

    @Test
    fun eraseTextsSweepBoundsGrowWithPadding() {
        // Box starts at (6, 6). Sweep reaches only (5, 5); the default 4pt padding must bridge it.
        val near = TextRecord("t1", "hi", 0f, 0f, 12f, "#FF000000", PageRect(6f, 6f, 30f, 20f), "2026-01-01T00:00:00Z")
        val hit = eraseIntersectingTexts(1f, 1f, 5f, 5f, listOf(near))
        assertEquals(setOf("t1"), hit)
        // Without padding the sweep (r=5, b=5) would not reach the box at l=6.
        val noPad = eraseIntersectingTexts(1f, 1f, 5f, 5f, listOf(near), eraserPaddingPt = 0f)
        assertTrue(noPad.isEmpty())
    }

    @Test
    fun eraseTextsReturnsEmptyForEmptyInput() {
        assertTrue(eraseIntersectingTexts(0f, 0f, 10f, 10f, emptyList()).isEmpty())
    }
}
