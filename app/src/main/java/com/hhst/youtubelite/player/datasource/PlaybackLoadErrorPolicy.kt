@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.hhst.youtubelite.player.datasource

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.hhst.youtubelite.diagnostics.AppLog

/**
 * Let source recovery own invalid URLs and initialization requests that never made progress.
 * [startupRetry], when armed, first retries a refused opening segment on the same variant.
 */
internal class PlaybackLoadErrorPolicy(
    private val startupRetry: HlsStartupRetry? = null,
) : DefaultLoadErrorHandlingPolicy() {
    private fun startupDelay(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long? = startupRetry?.delayMs(
        PlaybackRecovery.classify(info.exception) == PlaybackRecovery.Reason.HTTP_403,
        info.mediaLoadData.dataType, info.errorCount,
    )

    /** No variant is excluded while the same one is being retried. */
    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? =
        if (startupDelay(loadErrorInfo) != null) null else super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)

    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        startupDelay(info)?.let {
            AppLog.event(AppLog.Category.PLAYER, "hls_startup_retry", mapOf("error_count" to info.errorCount, "delay_ms" to it))
            return it
        }
        return when (PlaybackRecovery.classify(info.exception)) {
            PlaybackRecovery.Reason.HTTP_403, PlaybackRecovery.Reason.URL_STALE,
            PlaybackRecovery.Reason.SESSION_CHANGED -> C.TIME_UNSET
            PlaybackRecovery.Reason.TRANSPORT_FAILED ->
                if (info.mediaLoadData.dataType == C.DATA_TYPE_MEDIA_INITIALIZATION &&
                    info.loadEventInfo.bytesLoaded == 0L) C.TIME_UNSET
                else super.getRetryDelayMsFor(info)
            null -> super.getRetryDelayMsFor(info)
        }
    }
}

/**
 * The muxed HLS variants of a signed-in session refuse their opening segments with HTTP 403 for
 * about 2.5 s, whichever variant is asked. Media3 answers each refusal by excluding the variant;
 * with only one language's variants left the list runs out and playback falls into the slow source
 * recovery. For a few seconds after such a master loaded, the same variant is retried instead.
 * The three retries stay within Media3's minimum retry count for a media load.
 */
internal class HlsStartupRetry(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    @Volatile private var masterAt = Long.MIN_VALUE

    /** Arms the retry: a master without audio renditions was parsed. Only the first call counts. */
    fun masterLoaded() {
        if (masterAt == Long.MIN_VALUE) masterAt = clock()
    }

    /** Delay before the same segment is requested again, or null to keep the normal handling. */
    fun delayMs(forbidden: Boolean, dataType: Int, errorCount: Int): Long? {
        val armedAt = masterAt
        if (!forbidden || dataType != C.DATA_TYPE_MEDIA || armedAt == Long.MIN_VALUE) return null
        if (errorCount !in 1..MAX_RETRIES || clock() - armedAt >= WINDOW_MS) return null
        return 400L + 300L * errorCount
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val WINDOW_MS = 5_000L
    }
}
