package com.moonforce.ohmyainote.document.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OmaPressureInkV1Test {
    @Test
    fun pressureCurveHasStableVersionedBoundaries() {
        assertEquals(0.55f, OmaPressureInkV1.widthMultiplier(0f), 0.0001f)
        assertEquals(1f, OmaPressureInkV1.widthMultiplier(0.8f), 0.0001f)
        assertEquals(1.5f, OmaPressureInkV1.widthMultiplier(1f), 0.0001f)
        assertEquals(0.35f, OmaPressureInkV1.opacityMultiplier(0f), 0.0001f)
        assertEquals(1f, OmaPressureInkV1.opacityMultiplier(1f), 0.0001f)
    }
}
