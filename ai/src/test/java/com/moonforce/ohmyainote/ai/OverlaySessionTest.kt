package com.moonforce.ohmyainote.ai

import com.moonforce.ohmyainote.ai.api.AiAnswer
import com.moonforce.ohmyainote.document.model.PageRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySessionTest {

    private val region = RasterizedRegion(byteArrayOf(1), 100, 100)
    private val selection = PageRect(10f, 20f, 60f, 40f)

    private fun session(pageWidth: Float = 100f, pageHeight: Float = 200f) =
        OverlaySession("0001", selection, region)

    private fun answer(text: String = "回答内容") = AiAnswer(text, "model-x", 123L)

    @Test
    fun addTrimsAndRejectsBlank() {
        val s = session()
        s.add("  问题  ", answer())
        assertEquals(listOf("问题"), s.turns.map { it.question })
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { s.add("  ", answer()) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { s.add("q", answer("   ")) }
    }

    @Test
    fun currentCardRequiresAtLeastOneTurn() {
        val s = session()
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            s.currentCard(100f, 200f, "media/cards/x.jpg")
        }
    }

    @Test
    fun cardAnchorsRightOfSelectionWhenSpaceAllows() {
        val s = session(pageWidth = 595f, pageHeight = 842f)
        s.add("q", answer())
        val card = s.currentCard(595f, 842f, "thumb.jpg", "card-1")
        assertEquals(68f, card.anchor.x) // selection.r(60) + 8
        assertEquals(20f, card.anchor.y) // selection.t
        assertEquals(160f, card.anchor.w)
        assertEquals(96f, card.anchor.h)
        assertEquals("card-1", card.id)
        assertEquals("model-x", card.model)
    }

    @Test
    fun cardWrapsBelowSelectionWhenRightSideIsFull() {
        // PageRect(l=50, t=20, r=500, b=40). x = r+8 = 508, and 508+160 = 668 > 595,
        // so the card wraps below the selection: x = min(l, 595-160) = 50, y = b+8 = 48.
        val s = OverlaySession("0001", PageRect(50f, 20f, 500f, 40f), region)
        s.add("q", answer())
        val card = s.currentCard(595f, 842f, "thumb.jpg", "card-1")
        assertEquals(50f, card.anchor.x) // min(selection.l(50), 595-160=435)
        assertEquals(48f, card.anchor.y) // selection.b(40) + 8
    }

    @Test
    fun cardClampsIntoPageWhenBottomIsFull() {
        // Selection near the bottom edge so y + 96 would exceed the page; the card
        // must be clamped back inside the page bounds.
        val s = OverlaySession("0001", PageRect(10f, 800f, 60f, 820f), region)
        s.add("q", answer())
        val card = s.currentCard(595f, 842f, "thumb.jpg", "card-1")
        assertTrue("y+h must stay within page", card.anchor.y + card.anchor.h <= 842f)
        assertTrue("x+w must stay within page", card.anchor.x + card.anchor.w <= 595f)
        assertTrue("y must be non-negative", card.anchor.y >= 0f)
        assertTrue("x must be non-negative", card.anchor.x >= 0f)
    }
}
