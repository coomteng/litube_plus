package com.hhst.youtubelite.player.datasource

import com.hhst.youtubelite.extractor.Format
import java.net.URLDecoder

/**
 * Some HLS masters carry no audio renditions: every variant is one muxed stream and each audio
 * language is a full set of variants of its own, told apart only by `xtags=acont=…:lang=…` inside
 * the variant URL. Left alone, the player sees them as equal video qualities, so the language that
 * plays is whichever variant adaptation lands on, and the audio menu has nothing to list.
 */
object MuxedHlsAudio {
    /** [keep] indexes the variants to play; [selectedKey] is null when no track could be singled out. */
    data class Selection(val keep: List<Int>, val choices: List<AudioTrackChoice>, val selectedKey: String?)

    private data class Track(val type: String, val language: String) {
        val format = Format(audioLocale = language, audioTrackType = type.ifEmpty { null })
        val key = AudioTrackIdentity.key(format)
    }

    private val xtags = Regex("xtags=([^/;&]*)")

    /**
     * Variants of the original track by default, of [trackKey]'s track when it names one. Null when
     * the URLs do not describe at least two tracks. When no original is marked, the untagged
     * variants are kept, or everything when there are none.
     */
    fun select(variantUrls: List<String>, trackKey: String?): Selection? {
        val tracks = variantUrls.map(::trackOf)
        val distinct = tracks.filterNotNull().distinct()
        if (distinct.size < 2) return null
        val selected = trackKey?.takeIf { it.isNotBlank() }?.let { find(distinct, it) }
            ?: distinct.singleOrNull { it.type == "original" }
        // YouTube also lists the default track once more without xtags. With no
        // original to single out, those variants are the only ones known not to be a dub.
        val untagged = variantUrls.indices.filter { tracks[it] == null }
        return Selection(
            keep = if (selected != null) variantUrls.indices.filter { tracks[it] == selected }
                else untagged.ifEmpty { variantUrls.indices.toList() },
            choices = distinct.map {
                AudioTrackChoice(it.key, AudioTrackIdentity.label(it.format, showType = true), it.language, it.type.ifEmpty { null })
            },
            selectedKey = selected?.key,
        )
    }

    private fun trackOf(url: String): Track? {
        val decoded = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrNull() ?: return null
        val pairs = xtags.find(decoded)?.groupValues?.get(1)?.split(':')
            ?.associate { it.substringBefore('=') to it.substringAfter('=', "") } ?: return null
        val language = pairs["lang"]?.takeIf { it.isNotBlank() } ?: return null
        return Track(pairs["acont"].orEmpty(), language)
    }

    /** Also accepts the keys of the other menus (`id:de.3`, `hls:de:Deutsch - dubbed`) and a bare language. */
    private fun find(tracks: List<Track>, key: String): Track? {
        tracks.firstOrNull { it.key == key }?.let { return it }
        val language = when {
            key.startsWith("id:") -> key.removePrefix("id:").substringBefore('.')
            key.startsWith("hls:") -> key.removePrefix("hls:").substringBefore(':')
            key.startsWith("loc:") || key.startsWith("name:") -> return null
            else -> key
        }
        val matching = tracks.filter { it.language.equals(language, ignoreCase = true) }
            .ifEmpty { tracks.filter { it.language.substringBefore('-').equals(language.substringBefore('-'), ignoreCase = true) } }
        return matching.singleOrNull() ?: matching.firstOrNull { it.type == "original" }
    }
}
