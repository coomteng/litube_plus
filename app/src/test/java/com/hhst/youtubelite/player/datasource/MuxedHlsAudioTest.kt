package com.hhst.youtubelite.player.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Masters YouTube served the signed-in WEB_SAFARI client on 2026-11-19 (hl=en), with signatures and
 * session values redacted and the subtitle lines removed: no audio renditions, every variant muxed
 * (itag 91-96), one set of variants per audio track plus an untagged copy of the default track.
 */
class MuxedHlsAudioTest {
    private class Variant(val url: String, val height: Int, val contentId: String?)

    private fun master(videoId: String): List<Variant> {
        val lines = javaClass.getResourceAsStream("/hls/web_safari_signed_in_$videoId.m3u8")!!
            .bufferedReader().readLines()
        return lines.mapIndexedNotNull { i, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@mapIndexedNotNull null
            Variant(
                lines[i + 1],
                Regex("RESOLUTION=\\d+x(\\d+)").find(line)!!.groupValues[1].toInt(),
                Regex("YT-EXT-AUDIO-CONTENT-ID=\"([^\"]*)\"").find(line)?.groupValues?.get(1),
            )
        }
    }

    private fun select(variants: List<Variant>, key: String?) = MuxedHlsAudio.select(variants.map { it.url }, key)

    /** The audio tracks of the variants a selection keeps, by the manifest's own content id. */
    private fun keptTracks(variants: List<Variant>, key: String?) =
        select(variants, key)!!.keep.map { variants[it].contentId }.distinct()

    private val chinese = master("Q1EoisvxUQw")
    private val english = master("0e3GPea1Tyg")

    @Test
    fun chineseVideoWithEnglishAutoDubPlaysTheChineseOriginal() {
        assertEquals(12, chinese.size)
        val selection = select(chinese, null)!!
        assertEquals("loc:zh-Hant|original", selection.selectedKey)
        assertEquals(listOf("zh-Hant.4"), keptTracks(chinese, null))
        // Every quality stays available.
        assertEquals(listOf(144, 240, 360, 480), selection.keep.map { chinese[it].height })
        assertEquals(listOf("loc:en-US|dubbed-auto", "loc:zh-Hant|original"), selection.choices.map { it.key })
        assertEquals(listOf("dubbed-auto", "original"), selection.choices.map { it.trackType })
        assertEquals(listOf("en-US", "zh-Hant"), selection.choices.map { it.languageTag })
    }

    @Test
    fun englishVideoWithManyDubsPlaysTheEnglishOriginal() {
        assertEquals(150, english.size)
        val selection = select(english, null)!!
        assertEquals("loc:en-US|original", selection.selectedKey)
        assertEquals(listOf("en-US.4"), keptTracks(english, null))
        assertEquals(listOf(144, 240, 360, 480, 720, 1080), selection.keep.map { english[it].height })
        assertEquals(24, selection.choices.size)
        assertEquals(1, selection.choices.count { it.trackType == "original" })
        assertEquals(23, selection.choices.count { it.trackType == "dubbed" })
        assertTrue(selection.choices.any { it.key == "loc:zh-Hans|dubbed" })
    }

    @Test
    fun aPickedTrackPlaysThatLanguageAtEveryQuality() {
        assertEquals(listOf("en-US.10"), keptTracks(chinese, "loc:en-US|dubbed-auto"))
        assertEquals("loc:en-US|dubbed-auto", select(chinese, "loc:en-US|dubbed-auto")!!.selectedKey)
        val german = select(english, "loc:de|dubbed")!!
        assertEquals(listOf("de.3"), keptTracks(english, "loc:de|dubbed"))
        assertEquals(listOf(144, 240, 360, 480, 720, 1080), german.keep.map { english[it].height })
        // Chinese has two dubs; each key finds its own.
        assertEquals(listOf("zh-Hant.3"), keptTracks(english, "loc:zh-Hant|dubbed"))
        assertEquals(listOf("zh-Hans.3"), keptTracks(english, "loc:zh-Hans|dubbed"))
    }

    @Test
    fun aKeyOfAnotherMenuStillFindsItsTrack() {
        // Extractor-menu key, rendition-menu key and a remembered bare language.
        assertEquals(listOf("en-US.10"), keptTracks(chinese, "id:en-US.10"))
        assertEquals(listOf("zh-Hant.4"), keptTracks(chinese, "hls:zh-Hant:zh-Hant - original"))
        assertEquals(listOf("de.3"), keptTracks(english, "de"))
    }

    @Test
    fun anUnknownKeyFallsBackToTheOriginal() {
        assertEquals(listOf("en-US.4"), keptTracks(english, "id:sv.1"))
        assertEquals(listOf("en-US.4"), keptTracks(english, "loc:sv|dubbed"))
    }

    @Test
    fun withoutAMarkedOriginalTheUntaggedDefaultPlays() {
        val dubsOnly = chinese.filter { it.contentId != "zh-Hant.4" } +
            english.filter { it.contentId == "de.3" && it.height <= 480 }
        val selection = select(dubsOnly, null)!!
        assertNull(selection.selectedKey)
        assertEquals(listOf<String?>(null), keptTracks(dubsOnly, null))
        assertEquals(listOf(144, 240, 360, 480), selection.keep.map { dubsOnly[it].height })
        // The dubs can still be picked.
        assertEquals(listOf("de.3"), keptTracks(dubsOnly, "loc:de|dubbed"))
    }

    @Test
    fun nothingIsDroppedWhenNoTrackCanBeSingledOut() {
        val tagged = english.filter { it.contentId == "de.3" || it.contentId == "fr.3" }
        val selection = select(tagged, null)!!
        assertNull(selection.selectedKey)
        assertEquals(tagged.indices.toList(), selection.keep)
        assertEquals(listOf("loc:de|dubbed", "loc:fr|dubbed"), selection.choices.map { it.key })
    }

    @Test
    fun aSingleTrackMasterIsLeftAlone() {
        val single = master("single_track")
        assertEquals(6, single.size)
        assertNull(select(single, null))
        assertNull(select(single, "loc:de|dubbed"))
        assertNull(select(chinese.filter { it.contentId == null || it.contentId == "zh-Hant.4" }, null))
        assertNull(MuxedHlsAudio.select(emptyList(), null))
        assertNull(MuxedHlsAudio.select(listOf("null", "%zz"), null))
    }
}
