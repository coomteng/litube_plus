package com.hhst.youtubelite.extractor

import com.grack.nanojson.JsonParser
import com.hhst.youtubelite.downloader.core.DownloadConfig
import com.hhst.youtubelite.downloader.resolve.DownloadSelection
import com.hhst.youtubelite.downloader.resolve.DownloadSelector
import com.hhst.youtubelite.downloader.resolve.catalog
import com.hhst.youtubelite.player.datasource.AudioTrackIdentity
import com.hhst.youtubelite.player.datasource.CastAudioSelection
import com.hhst.youtubelite.player.datasource.StreamSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.streams.FormatKey
import org.schabi.newpipe.extractor.services.youtube.streams.RequestPlan
import org.schabi.newpipe.extractor.services.youtube.streams.StreamCandidate

/**
 * Track sets captured 2026-11-19 with hl=en (the only language the extractor
 * requests): `audioTrack` objects and `xtags` exactly as YouTube returned them.
 */
class AudioTrackTypesTest {

    @Test
    fun decodesAcontFromProtobufXtags() {
        assertEquals("original", AudioTrackTypes.acont("ChEKBWFjb250EghvcmlnaW5hbAoPCgRsYW5nEgd6aC1IYW50"))
        assertEquals("dubbed-auto", AudioTrackTypes.acont("ChQKBWFjb250EgtkdWJiZWQtYXV0bwoNCgRsYW5nEgVlbi1VUw"))
        assertEquals("dubbed", AudioTrackTypes.acont("Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICYXI"))
        // acont + drc + lang
        assertEquals("original", AudioTrackTypes.acont("ChEKBWFjb250EghvcmlnaW5hbAoICgNkcmMSATEKDwoEbGFuZxIHemgtSGFudA"))
        // The URL form of the same value.
        assertEquals("descriptive", AudioTrackTypes.acont("acont=descriptive:lang=en"))
        assertNull(AudioTrackTypes.acont("lang=en"))
        assertNull(AudioTrackTypes.acont(null))
        assertNull(AudioTrackTypes.acont(""))
        assertNull(AudioTrackTypes.acont("!!not base64!!"))
        assertNull(AudioTrackTypes.acont("ChEKBWFjb250Egh"))
    }

    @Test
    fun chineseVideoWithEnglishAutoDubPicksChineseOnWeb() {
        // Q1EoisvxUQw, WEB: the auto-dub is "default" for hl=en.
        val formats = formats(
            track("English (US)", "en-US.10", default = true, autoDubbed = true,
                xtags = "ChQKBWFjb250EgtkdWJiZWQtYXV0bwoNCgRsYW5nEgVlbi1VUw", bitrate = 160_000),
            track("Chinese (Traditional) original", "zh-Hant.4", default = false,
                xtags = "ChEKBWFjb250EghvcmlnaW5hbAoICgNkcmMSATEKDwoEbGFuZxIHemgtSGFudA"),
        )
        assertDefault("zh-Hant.4", formats)
        val dub = formats.single { it.audioTrackId == "en-US.10" }
        assertEquals("dubbed-auto", dub.audioTrackType)
        assertFalse(AudioTrackIdentity.isOriginal(dub))
    }

    @Test
    fun chineseVideoWithEnglishAutoDubPicksChineseOnVisionOs() {
        // Q1EoisvxUQw, VISIONOS.
        val formats = formats(
            track("English (US)", "en-US.10", default = false, autoDubbed = true,
                xtags = "ChQKBWFjb250EgtkdWJiZWQtYXV0bwoNCgRsYW5nEgVlbi1VUw", bitrate = 160_000),
            track("Chinese (Traditional) original", "zh-Hant.4", default = true,
                xtags = "ChEKBWFjb250EghvcmlnaW5hbAoPCgRsYW5nEgd6aC1IYW50"),
        )
        assertDefault("zh-Hant.4", formats)
    }

    @Test
    fun englishVideoWithManyDubsPicksEnglish() {
        // 0e3GPea1Tyg: en-US original + 23 creator dubs.
        val dubs = listOf(
            "Arabic" to "ar.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICYXI",
            "Bangla" to "bn.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICYm4",
            "German" to "de.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICZGU",
            "Spanish" to "es.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICZXM",
            "French" to "fr.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICZnI",
            "Hindi" to "hi.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICaGk",
            "Indonesian" to "id.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICaWQ",
            "Italian" to "it.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICaXQ",
            "Japanese" to "ja.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICamE",
            "Korean" to "ko.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICa28",
            "Malayalam" to "ml.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICbWw",
            "Marathi" to "mr.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICbXI",
            "Punjabi" to "pa.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICcGE",
            "Polish" to "pl.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICcGw",
            "Portuguese" to "pt.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICcHQ",
            "Russian" to "ru.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICcnU",
            "Tamil" to "ta.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICdGE",
            "Telugu" to "te.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICdGU",
            "Thai" to "th.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICdGg",
            "Turkish" to "tr.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICdHI",
            "Vietnamese" to "vi.3" to "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICdmk",
            "Chinese (Simplified)" to "zh-Hans.3" to "Cg8KBWFjb250EgZkdWJiZWQKDwoEbGFuZxIHemgtSGFucw",
            "Chinese (Traditional)" to "zh-Hant.3" to "Cg8KBWFjb250EgZkdWJiZWQKDwoEbGFuZxIHemgtSGFudA",
        ).map { (track, xtags) -> track(track.first, track.second, default = false, xtags = xtags, bitrate = 160_000) }
        val original = track("English (US) original", "en-US.4", default = true,
            xtags = "ChEKBWFjb250EghvcmlnaW5hbAoICgNkcmMSATEKDQoEbGFuZxIFZW4tVVM")
        val formats = formats(*(dubs + original).toTypedArray())
        assertEquals(24, formats.size)
        assertDefault("en-US.4", formats)
        assertTrue(formats.filter { it.audioTrackId != "en-US.4" }.all { it.audioTrackType == "dubbed" })
    }

    @Test
    fun displayNameIdentifiesOriginalWhenXtagsAreMissing() {
        val formats = formats(
            track("English (US)", "en-US.10", default = true, autoDubbed = true, xtags = null, bitrate = 160_000),
            track("Chinese (Traditional) original", "zh-Hant.4", default = false, xtags = null),
        )
        assertDefault("zh-Hant.4", formats)
    }

    @Test
    fun requestLanguageDefaultIsOnlyTheLastFallback() {
        // No xtags, no "original" in any name: the default flag is all there is.
        val formats = formats(
            track("English", "en.3", default = true, xtags = null),
            track("Spanish", "es.3", default = false, xtags = null, bitrate = 160_000),
        )
        assertDefault("en.3", formats)
    }

    @Test
    fun autoDubIsNeverOriginal() {
        // Not even as the request-language default with nothing else to go by ...
        val unknown = formats(
            track("English (US)", "en-US.10", default = true, autoDubbed = true, xtags = null),
            track("Chinese (Traditional)", "zh-Hant.4", default = false, xtags = null),
        )
        assertTrue(unknown.none(AudioTrackIdentity::isOriginal))
        assertEquals("dubbed-auto", unknown.single { it.audioTrackId == "en-US.10" }.audioTrackType)
        assertEquals("zh-Hant.4", StreamSelection.selectAudio(unknown)?.audioTrackId)
        // ... nor when its own xtags would claim it.
        val lying = formats(track("English (US)", "en-US.10", default = true, autoDubbed = true,
            xtags = "ChEKBWFjb250EghvcmlnaW5hbAoNCgRsYW5nEgVlbi1VUw"))
        assertTrue(lying.none(AudioTrackIdentity::isOriginal))
    }

    @Test
    fun extractorTypedFormatIsCorrectedFromTheCandidate() {
        // The NewPipe path types a format from its URL only, and leaves it untyped without xtags there.
        val dub = candidate(track("English (US)", "en-US.10", default = true, autoDubbed = true,
            xtags = "ChQKBWFjb250EgtkdWJiZWQtYXV0bwoNCgRsYW5nEgVlbi1VUw"))
        val original = candidate(track("Chinese (Traditional) original", "zh-Hant.4", default = false,
            xtags = "ChEKBWFjb250EghvcmlnaW5hbAoPCgRsYW5nEgd6aC1IYW50"))
        val plain = Format(url = "u", audioOnly = true, audioTrackId = "x")
        assertEquals("dubbed-auto", plain.withAudioTrackType(dub).audioTrackType)
        val typed = plain.withAudioTrackType(original)
        assertEquals("original", typed.audioTrackType)
        assertTrue(typed.audioTrackOriginal)
        // A video format is left alone.
        assertNull(Format(url = "v", videoOnly = true).withAudioTrackType(original).audioTrackType)
    }

    @Test
    fun autoDubIsSelectableAndLabelled() {
        val formats = formats(
            track("English (US)", "en-US.10", default = true, autoDubbed = true,
                xtags = "ChQKBWFjb250EgtkdWJiZWQtYXV0bwoNCgRsYW5nEgVlbi1VUw"),
            track("Chinese (Traditional) original", "zh-Hant.4", default = false,
                xtags = "ChEKBWFjb250EghvcmlnaW5hbAoPCgRsYW5nEgd6aC1IYW50"),
        )
        val choices = AudioTrackIdentity.choices(formats)
        assertEquals(listOf("id:en-US.10", "id:zh-Hant.4"), choices.map { it.key })
        assertEquals("dubbed-auto", choices[0].trackType)
        assertTrue(choices[0].label.endsWith("Dubbed (auto)"))
        assertEquals("en-US.10", StreamSelection.selectAudio(formats, "id:en-US.10")?.audioTrackId)
    }

    private fun assertDefault(trackId: String, formats: List<Format>) {
        assertEquals(listOf(trackId), formats.filter(AudioTrackIdentity::isOriginal).map { it.audioTrackId })
        assertEquals(trackId, StreamSelection.selectAudio(formats)?.audioTrackId)
        assertEquals(trackId, CastAudioSelection.select(formats, null) { true }?.audioTrackId)
        val download = DownloadSelector.select(catalog(formats), DownloadConfig(audioOnly = true))
        assertEquals(trackId, (download as DownloadSelection.Ready).plan.audio?.format?.audioTrackId)
    }

    private fun track(
        name: String,
        id: String,
        default: Boolean,
        xtags: String?,
        autoDubbed: Boolean = false,
        bitrate: Int = 130_000,
    ): String = buildString {
        append("""{"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":$bitrate,""")
        append(""""initRange":{"start":"0","end":"631"},"indexRange":{"start":"632","end":"1500"},""")
        if (xtags != null) append(""""xtags":"$xtags",""")
        append(""""audioTrack":{"displayName":"$name","id":"$id","audioIsDefault":$default""")
        if (autoDubbed) append(""","isAutoDubbed":true""")
        append("}}")
    }

    private fun candidate(json: String): StreamCandidate {
        val format = JsonParser.`object`().from(json)
        val id = format.getObject("audioTrack").getString("id")
        return StreamCandidate(
            FormatKey(140, id, "mp4a.40.2", false, false, RequestPlan.Protocol.HTTPS),
            "https://gv/videoplayback?itag=140&track=$id", "", 0L, "", null, format, true, false,
        )
    }

    private fun formats(vararg tracks: String): List<Format> {
        val candidates = tracks.map(::candidate)
        return AudioTrackTypes.withDefaultFallback(candidates.map { it.asFormat() }, candidates)
    }
}
