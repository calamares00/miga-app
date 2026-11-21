package org.calamares.miga.data.search

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.withContext
import org.calamares.miga.data.support.AppDispatchers
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * The sound of a post's video, in the two forms the providers take: [aac] (raw AAC frames with
 * ADTS headers, for Gemini; null when the track is not plain AAC) and [m4a] (the track alone in an
 * MP4 file, for the transcription services). Taken out of the video as it is, without re-encoding:
 * about 0.7 MB per minute instead of the video's 15 MB.
 */
class PostAudio(val aac: ByteArray?, val m4a: ByteArray, val seconds: Int)

object PostAudioExtractor {

    private const val MAX_VIDEO_BYTES = 150L * 1024 * 1024

    /** Longer videos are cut: recipes are told in the first minutes, and the request stays small. */
    private const val MAX_SECONDS = 15 * 60

    private const val DEFAULT_SAMPLE_BUFFER = 256 * 1024

    /** Downloads the video at [videoUrl] and takes its audio out; null when either fails. */
    suspend fun fromVideo(context: Context, videoUrl: String): PostAudio? = withContext(AppDispatchers.io) {
        val dir = File(context.cacheDir, "post-video").apply { mkdirs() }
        val video = File(dir, "video.mp4")
        val m4a = File(dir, "audio.m4a")
        try {
            if (!RecipeUrlFetcher.downloadToFile(videoUrl, video, MAX_VIDEO_BYTES)) return@withContext null
            runCatching { extract(video, m4a) }.getOrNull()
        } finally {
            video.delete()
            m4a.delete()
        }
    }

    private fun extract(video: File, m4aFile: File): PostAudio? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(video.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC
            } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val config = format.getByteBuffer("csd-0")?.let { buffer -> ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) } }
            val adts = AdtsHeader.from(config)
            muxer = MediaMuxer(m4aFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxOf(format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), DEFAULT_SAMPLE_BUFFER)
            } else DEFAULT_SAMPLE_BUFFER
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            val aac = ByteArrayOutputStream()
            var lastMicros = 0L
            var samples = 0
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0 || extractor.sampleTime > MAX_SECONDS * 1_000_000L) break
                if (adts != null) {
                    val frame = ByteArray(size)
                    buffer.position(0)
                    buffer.get(frame, 0, size)
                    aac.write(adts.forFrame(size))
                    aac.write(frame)
                }
                buffer.position(0)
                buffer.limit(size)
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, extractor.sampleTime, flags)
                muxer.writeSampleData(outTrack, buffer, info)
                lastMicros = extractor.sampleTime
                samples++
                extractor.advance()
            }
            if (samples == 0) return null
            muxer.stop()
            muxer.release()
            muxer = null
            return PostAudio(
                aac = if (adts != null) aac.toByteArray() else null,
                m4a = m4aFile.readBytes(),
                seconds = (lastMicros / 1_000_000L).toInt() + 1
            )
        } finally {
            muxer?.let { runCatching { it.release() } }
            extractor.release()
        }
    }
}

/**
 * The 7-byte ADTS header that goes before each raw AAC frame so the stream can be read on its own
 * (an ".aac" file), built from the track's AudioSpecificConfig.
 */
internal class AdtsHeader(private val objectType: Int, private val frequencyIndex: Int, private val channels: Int) {

    fun forFrame(payloadSize: Int): ByteArray {
        val length = payloadSize + HEADER_SIZE
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(), // MPEG-4, layer 0, no CRC
            (((objectType - 1) shl 6) or (frequencyIndex shl 2) or (channels shr 2)).toByte(),
            (((channels and 3) shl 6) or (length shr 11)).toByte(),
            ((length shr 3) and 0xFF).toByte(),
            (((length and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte() // buffer fullness (variable) and one frame
        )
    }

    companion object {
        const val HEADER_SIZE = 7

        private const val AAC_LC = 2
        private const val SBR = 5
        private const val PARAMETRIC_STEREO = 29

        /**
         * From the AudioSpecificConfig (5 bits object type, 4 bits frequency index, 4 bits channels).
         * HE-AAC (SBR, and parametric stereo), which Instagram uses, goes as its AAC-LC core:
         * decoders find the extension in the frames themselves. Null for what ADTS cannot carry
         * (explicit frequencies, other object types, odd channel setups).
         */
        fun from(config: ByteArray?): AdtsHeader? {
            if (config == null || config.size < 2) return null
            val first = config[0].toInt() and 0xFF
            val second = config[1].toInt() and 0xFF
            val objectType = (first shr 3).let { if (it == SBR || it == PARAMETRIC_STEREO) AAC_LC else it }
            val frequencyIndex = ((first and 0x07) shl 1) or (second shr 7)
            val channels = (second shr 3) and 0x0F
            if (objectType !in 1..4 || frequencyIndex > 12 || channels !in 1..7) return null
            return AdtsHeader(objectType, frequencyIndex, channels)
        }
    }
}
