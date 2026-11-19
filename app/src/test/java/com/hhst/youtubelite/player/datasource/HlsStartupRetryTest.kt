package com.hhst.youtubelite.player.datasource

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsStartupRetryTest {
    private var now = 10_000L
    private val retry = HlsStartupRetry { now }

    private fun forbidden(errorCount: Int, type: Int = C.DATA_TYPE_MEDIA) =
        retry.delayMs(forbidden = true, dataType = type, errorCount = errorCount)

    @Test
    fun aRefusedFirstSegmentIsRetriedOnTheSameVariantForAboutThreeSeconds() {
        retry.masterLoaded()
        now += 800
        // Measured on the phone: refusals stop about 2.5 s after they begin.
        val delays = (1..3).map { count -> forbidden(count)!!.also { now += it } }
        assertEquals(listOf(700L, 1_000L, 1_300L), delays)
        // The fourth refusal goes back to Media3's variant exclusion and source recovery.
        assertNull(forbidden(4))
    }

    @Test
    fun nothingChangesBeforeAMuxedMasterWasSeen() {
        assertNull(forbidden(1))
    }

    @Test
    fun aRefusalAfterTheStartWindowIsLeftToRecovery() {
        retry.masterLoaded()
        now += 5_000
        assertNull(forbidden(1))
        // A reload of the master (there is none for VOD) must not reopen the window.
        retry.masterLoaded()
        assertNull(forbidden(1))
    }

    @Test
    fun onlyRefusedMediaSegmentsAreRetried() {
        retry.masterLoaded()
        assertNull(retry.delayMs(forbidden = false, dataType = C.DATA_TYPE_MEDIA, errorCount = 1))
        assertNull(forbidden(1, C.DATA_TYPE_MANIFEST))
        assertNull(forbidden(1, C.DATA_TYPE_MEDIA_INITIALIZATION))
        assertEquals(700L, forbidden(1))
    }
}
