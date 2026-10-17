package dev.liquidglass.ios27

import org.junit.Assert.*
import org.junit.Test

class Ios27AndroidBehaviorTest {
    @Test fun invalidProgressIsRejected() {
        assertNull(androidProgressValue(Float.NaN, 0f..1f))
        assertNull(androidProgressValue(Float.POSITIVE_INFINITY, 0f..1f))
        assertNull(androidProgressValue(Float.NEGATIVE_INFINITY, 0f..1f))
    }

    @Test fun progressStopsAtBothTrackEnds() {
        assertEquals(0f, androidProgressValue(-2f, 0f..1f)!!, 0f)
        assertEquals(1f, androidProgressValue(2f, 0f..1f)!!, 0f)
        assertEquals(0.625f, androidProgressValue(0.625f, 0f..1f)!!, 0f)
    }

    @Test fun continuousKeyboardAdjustmentsUseFivePercentSteps() {
        assertEquals(0.55f, androidProgressStep(0.5f, 0f..1f, 0, 1), 0.000001f)
        assertEquals(0.45f, androidProgressStep(0.5f, 0f..1f, 0, -1), 0.000001f)
        assertEquals(1f, androidProgressStep(1f, 0f..1f, 0, 1), 0f)
        assertEquals(0f, androidProgressStep(0f, 0f..1f, 0, -1), 0f)
    }

    @Test fun dateAndTimeAdvanceByExactlyOneValue() {
        assertEquals(2027f, androidProgressStep(2026f, 1900f..2100f, 199, 1), 0f)
        assertEquals(6f, androidProgressStep(5f, 0f..11f, 10, 1), 0f)
        assertEquals(59f, androidProgressStep(58f, 0f..59f, 58, 1), 0f)
    }

    @Test fun discreteAccessibilityRequestsSnapToRealValues() {
        assertEquals(2027f, androidProgressValue(2026.8f, 1900f..2100f, 199)!!, 0f)
        assertEquals(7f, androidProgressValue(6.8f, 0f..11f, 10)!!, 0f)
    }
}
