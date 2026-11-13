package ru.spgsroot.peachy.compress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionPlannerTest {
    private fun source(
        width: Int = 1920, height: Int = 1080, fps: Float = 30f, bitrate: Long = 16_000_000,
        durationMs: Long = 60_000, hasAudio: Boolean = true, isHevc: Boolean = false
    ) = SourceInfo(width, height, fps, bitrate, durationMs, bitrate * durationMs / 8000, hasAudio, isHevc)

    private fun settings(
        rate: RateControl = RateControl.Quality(0.10f), share: Float? = null, maxHeight: Int? = null,
        maxFps: Int? = null, codec: VideoCodec = VideoCodec.HEVC, audio: AudioMode = AudioMode.KEEP
    ) = CompressionSettings(codec, rate, share, maxHeight, maxFps, audio)

    @Test
    fun qualityFollowsFrameSizeAndRate() {
        val plan = CompressionPlanner.plan(source(), settings(), EncoderSupport.ALL)!!
        assertEquals(1920 * 1080 * 30 / 10, plan.videoBitrate)
        assertFalse(plan.scaled)
        assertFalse(plan.dropFrames)
    }

    @Test
    fun downscaleKeepsAspectWithEvenSides() {
        val plan = CompressionPlanner.plan(source(width = 1920, height = 1080), settings(maxHeight = 720), EncoderSupport.ALL)!!
        assertEquals(1280, plan.width)
        assertEquals(720, plan.height)
        assertTrue(plan.scaled)
        val odd = CompressionPlanner.plan(source(width = 1000, height = 563), settings(maxHeight = 300), EncoderSupport.ALL)!!
        assertEquals(0, odd.width % 2)
        assertEquals(0, odd.height % 2)
    }

    @Test
    fun heightLimitAboveSourceChangesNothing() {
        val plan = CompressionPlanner.plan(source(height = 480, width = 854), settings(maxHeight = 720), EncoderSupport.ALL)!!
        assertFalse(plan.scaled)
        assertEquals(480, plan.height)
    }

    @Test
    fun frameRateIsOnlyEverLowered() {
        assertTrue(CompressionPlanner.plan(source(fps = 60f), settings(maxFps = 30), EncoderSupport.ALL)!!.dropFrames)
        val same = CompressionPlanner.plan(source(fps = 24f), settings(maxFps = 30), EncoderSupport.ALL)!!
        assertFalse(same.dropFrames)
        assertEquals(24f, same.fps, 0.001f)
    }

    @Test
    fun capKeepsLeanFilesFromGrowing() {
        val lean = source(bitrate = 2_000_000)
        val plan = CompressionPlanner.plan(lean, settings(share = 0.5f), EncoderSupport.ALL)!!
        assertEquals(1_000_000, plan.videoBitrate)
    }

    @Test
    fun noHevcEncoderFallsBackToH264WithMoreBitrate() {
        val hevc = CompressionPlanner.plan(source(), settings(), EncoderSupport.ALL)!!
        val avc = CompressionPlanner.plan(source(), settings(), EncoderSupport.NO_HEVC)!!
        assertEquals(VideoCodec.H264, avc.codec)
        assertTrue(avc.videoBitrate > hevc.videoBitrate)
    }

    @Test
    fun hevcIsAskedAtTheOutputSizeNotTheSourceSize() {
        val asked = mutableListOf<Pair<Int, Int>>()
        val support = EncoderSupport { _, w, h, _ -> asked += w to h; true }
        CompressionPlanner.plan(source(width = 1920, height = 1080), settings(maxHeight = 720), support)!!
        assertEquals(listOf(1280 to 720), asked)
        val small = EncoderSupport { _, w, _, _ -> w <= 1280 }
        assertEquals(VideoCodec.H264, CompressionPlanner.plan(source(width = 1920, height = 1080), settings(), small)!!.codec)
        assertEquals(VideoCodec.HEVC, CompressionPlanner.plan(source(width = 1920, height = 1080), settings(maxHeight = 720), small)!!.codec)
    }

    @Test
    fun removedAudioIsNotCountedInEstimate() {
        val keep = CompressionPlanner.plan(source(), settings(), EncoderSupport.ALL)!!
        val drop = CompressionPlanner.plan(source(), settings(audio = AudioMode.REMOVE), EncoderSupport.ALL)!!
        assertTrue(drop.removeAudio)
        assertTrue(drop.estimatedBytes < keep.estimatedBytes)
    }

    @Test
    fun barelyShrinkingEncodeIsNotWorthRunning() {
        val src = source(bitrate = 3_000_000, isHevc = true)
        val plan = CompressionPlanner.plan(src, settings(share = 1.0f), EncoderSupport.ALL)!!
        assertFalse(CompressionPlanner.isWorthwhile(plan, src))
        val big = source(bitrate = 20_000_000)
        assertTrue(CompressionPlanner.isWorthwhile(CompressionPlanner.plan(big, CompressionPresets.visuallyLossless.settings, EncoderSupport.ALL)!!, big))
    }

    @Test
    fun unusableSourceHasNoPlan() {
        assertNull(CompressionPlanner.plan(source(durationMs = 0), settings(), EncoderSupport.ALL))
        assertNull(CompressionPlanner.plan(source(width = 0), settings(), EncoderSupport.ALL))
    }

    @Test
    fun outputCheckRejectsWhatShouldNotReplaceTheOriginal() {
        val src = source()
        val plan = CompressionPlanner.plan(src, settings(), EncoderSupport.ALL)!!
        val good = src.copy(sizeBytes = src.sizeBytes / 2)
        assertNull(OutputCheck.verdict(src, good, plan))
        assertEquals(Rejection.UNREADABLE, OutputCheck.verdict(src, null, plan))
        assertEquals(Rejection.NOT_SMALLER, OutputCheck.verdict(src, src.copy(sizeBytes = src.sizeBytes), plan))
        assertEquals(Rejection.DURATION, OutputCheck.verdict(src, good.copy(durationMs = src.durationMs / 2), plan))
        assertEquals(Rejection.FRAME_SIZE, OutputCheck.verdict(src, good.copy(height = 720, width = 1280), plan))
        assertEquals(Rejection.AUDIO_LOST, OutputCheck.verdict(src, good.copy(hasAudio = false), plan))
    }

    @Test
    fun audioMayBeMissingWhenItWasRemovedOnPurpose() {
        val src = source()
        val plan = CompressionPlanner.plan(src, settings(audio = AudioMode.REMOVE), EncoderSupport.ALL)!!
        assertNull(OutputCheck.verdict(src, src.copy(sizeBytes = src.sizeBytes / 2, hasAudio = false), plan))
    }
}
