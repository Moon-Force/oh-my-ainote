package com.moonforce.ohmyainote.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// TileKey is a pure value type. TileCache wraps android.util.LruCache which is a
// stubbed framework class on plain JVM (not mocked without Robolectric, which the
// offline mirror does not carry), so this class only exercises TileKey equality —
// the part that is framework-agnostic.
class TileKeyTest {

    @Test
    fun keysAreEqualWhenAllCoordinatesMatch() {
        assertEquals(TileKey("nb", 0, 2f, 3, 4), TileKey("nb", 0, 2f, 3, 4))
    }

    @Test
    fun keysDifferByAnySingleCoordinate() {
        val base = TileKey("nb", 0, 2f, 3, 4)
        assertTrue("notebookId", base != TileKey("nb2", 0, 2f, 3, 4))
        assertTrue("pageIndex", base != TileKey("nb", 1, 2f, 3, 4))
        assertTrue("scaleBucket", base != TileKey("nb", 0, 4f, 3, 4))
        assertTrue("tileX", base != TileKey("nb", 0, 2f, 4, 4))
        assertTrue("tileY", base != TileKey("nb", 0, 2f, 3, 5))
    }
}
