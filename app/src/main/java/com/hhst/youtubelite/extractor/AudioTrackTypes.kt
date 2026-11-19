package com.hhst.youtubelite.extractor

import com.grack.nanojson.JsonObject
import com.hhst.youtubelite.player.datasource.AudioTrackIdentity
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import org.schabi.newpipe.extractor.services.youtube.streams.StreamCandidate

/**
 * Audio track kind of a `/player` format: `original`, `dubbed`, `dubbed-auto`,
 * `descriptive` or `secondary`.
 *
 * `audioTrack.audioIsDefault` is NOT "original": it marks the default track for
 * the request language, and the extractor always asks with `hl=en`, so a
 * Chinese video with an English auto-dub reports the dub as default. The kind
 * is read from the language-independent `xtags` (`acont`), then from the
 * display name; `audioIsDefault` only decides when nothing else does.
 */
internal object AudioTrackTypes {
    private val plainAcont = Regex("(?:^|:)acont=([^:]+)")
    private val known = setOf("original", "dubbed", "dubbed-auto", "descriptive", "secondary")

    /** Kind of one format, or null when the format itself does not say. */
    fun of(format: JsonObject): String? {
        val track = format.getObject("audioTrack") ?: return null
        if (track.getBoolean("isAutoDubbed", false)) return "dubbed-auto"
        acont(format.getString("xtags", null))?.takeIf { it in known }?.let { return it }
        return "original".takeIf { AudioTrackIdentity.isOriginalLabel(track.getString("displayName", null)) }
    }

    /**
     * `acont` of an `xtags` value: URL form `acont=original:lang=en`, or the
     * format field's base64 protobuf, a list of key/value string pairs.
     */
    fun acont(xtags: String?): String? {
        if (xtags.isNullOrBlank()) return null
        plainAcont.find(xtags)?.let { return it.groupValues[1] }
        val bytes = xtags.decodeBase64() ?: return null
        return runCatching {
            val message = Buffer().write(bytes)
            while (!message.exhausted()) {
                val pair = message.field() ?: return@runCatching null
                var key: String? = null
                var value: String? = null
                while (!pair.exhausted()) {
                    val tag = pair.varint()
                    val text = (pair.field(tag) ?: return@runCatching null).readUtf8()
                    if (tag == 0x0AL) key = text else if (tag == 0x12L) value = text
                }
                if (key == "acont") return@runCatching value
            }
            null
        }.getOrNull()
    }

    /**
     * Last resort for a video whose tracks name no original: trust the
     * request-language default, unless it is an auto-dub or already has a kind.
     */
    fun withDefaultFallback(formats: List<Format>, candidates: List<StreamCandidate>): List<Format> {
        if (formats.any { it.audioOnly && AudioTrackIdentity.isOriginal(it) }) return formats
        val defaults = candidates.mapNotNull { candidate ->
            val track = candidate.format.getObject("audioTrack") ?: return@mapNotNull null
            track.getString("id", null).takeIf {
                track.getBoolean("audioIsDefault", false) && !track.getBoolean("isAutoDubbed", false)
            }
        }.toSet()
        if (defaults.isEmpty()) return formats
        return formats.map { format ->
            if (format.audioOnly && format.audioTrackType == null && format.audioTrackId in defaults) {
                format.copy(audioTrackType = "original", audioTrackOriginal = true)
            } else {
                format
            }
        }
    }

    /** Length-delimited protobuf field; null for any other wire type. */
    private fun Buffer.field(tag: Long = varint()): Buffer? {
        if (tag and 7L != 2L) return null
        return Buffer().also { it.write(this, varint()) }
    }

    private fun Buffer.varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = readByte().toLong()
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0L) return result
            shift += 7
            require(shift < 64)
        }
    }
}

/** The format's own JSON outranks what the extractor read from the URL. */
internal fun Format.withAudioTrackType(candidate: StreamCandidate): Format {
    if (!audioOnly) return this
    val type = AudioTrackTypes.of(candidate.format) ?: return this
    return copy(audioTrackType = type, audioTrackOriginal = type == "original")
}
