package com.playbox.app.games.nearby

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbySignalQualityTest {
    @Test
    fun `rssi maps to green yellow red thresholds`() {
        assertEquals(NearbySignalQuality.UNKNOWN, signalQualityFor(null))
        assertEquals(NearbySignalQuality.GOOD, signalQualityFor(-55))
        assertEquals(NearbySignalQuality.GOOD, signalQualityFor(-65))
        assertEquals(NearbySignalQuality.FAIR, signalQualityFor(-66))
        assertEquals(NearbySignalQuality.FAIR, signalQualityFor(-80))
        assertEquals(NearbySignalQuality.POOR, signalQualityFor(-81))
    }
}
