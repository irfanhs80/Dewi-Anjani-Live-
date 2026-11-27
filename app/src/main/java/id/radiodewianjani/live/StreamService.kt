package id.radiodewianjani.live

import android.app.*
import android.content.Intent
import android.media.*
import android.os.IBinder
import android.util.Base64
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Basic Icecast AAC source prototype.
 * Input: Android default audio source (USB interfaces supported by the device may be selected by Android).
 * Output: AAC-LC in ADTS framing over Icecast HTTP PUT.
 */
class StreamService : Service() {
    companion object {
        const val ACTION_START = "id.radiodewianjani.live.START"
        const val ACTION_STOP = "id.radiodewianjani.live.STOP"
        private const val CHANNEL = "stream_status"
    }
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var connection: HttpURLConnection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START && running.compareAndSet(false, true)) {
            createChannel()
            startForeground(7, notification("Menghubungkan ke Icecast…"))
            val host = intent.getStringExtra("host") ?: ""
            val port = intent.getIntExtra("port", 8000)
            val mount = intent.getStringExtra("mount") ?: "/live.aac"
            val user = intent.getStringExtra("user") ?: "source"
            val pass = intent.getStringExtra("pass") ?: ""
            worker = thread(name = "IcecastAudio") {
                try { stream(host, port, mount, user, pass) }
                catch (e: Exception) { android.util.Log.e("DewiLive", "Streaming gagal", e) }
                finally { stopStreaming() }
            }
        }
        return START_NOT_STICKY
    }

    private fun stream(host: String, port: Int, mount: String, user: String, pass: String) {
        require(host.isNotBlank() && mount.startsWith("/")) { "Host atau mount tidak valid" }
        val url = URL("http://$host:$port$mount")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            useCaches = false
            connectTimeout = 15000
            readTimeout = 15000
            setChunkedStreamingMode(0)
            setRequestProperty("Authorization", "Basic " +
                Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP))
            setRequestProperty("Content-Type", "audio/aac")
            setRequestProperty("Ice-Public", "0")
            setRequestProperty("Ice-Name", "Dewi Anjani Live")
            setRequestProperty("Ice-Description", "Live audio dari Android")
            setRequestProperty("Ice-Audio-Info", "samplerate=44100;channels=1;bitrate=128")
        }
        connection = conn
        conn.connect()
        if (conn.responseCode !in 200..299) throw IllegalStateException("Icecast menolak koneksi: HTTP ${conn.responseCode}")
        val out = conn.outputStream
        encodeTo(out)
    }

    private fun encodeTo(out: OutputStream) {
        val sampleRate = 44100
        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "Format input audio tidak didukung" }
        val input = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(min * 4)
            .build()
        recorder = input
        val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
        encoder = codec
        val format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, min * 2)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        input.startRecording()

        val pcm = ByteArray(min)
        val info = MediaCodec.BufferInfo()
        while (running.get()) {
            val n = input.read(pcm, 0, pcm.size)
            if (n > 0) {
                val idx = codec.dequeueInputBuffer(10000)
                if (idx >= 0) {
                    val buf = codec.getInputBuffer(idx)!!
                    buf.clear(); buf.put(pcm, 0, n)
                    codec.queueInputBuffer(idx, 0, n, System.nanoTime() / 1000, 0)
                }
            }
            var outIndex = codec.dequeueOutputBuffer(info, 0)
            while (outIndex >= 0) {
                val encoded = codec.getOutputBuffer(outIndex)
                if (encoded != null && info.size > 0 &&
                    (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    encoded.position(info.offset); encoded.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size); encoded.get(bytes)
                    out.write(adtsHeader(bytes.size, sampleRate, 1))
                    out.write(bytes)
                    out.flush()
                }
                codec.releaseOutputBuffer(outIndex, false)
                outIndex = codec.dequeueOutputBuffer(info, 0)
            }
        }
        input.stop()
        codec.stop()
        codec.release()
        input.release()
    }

    private fun adtsHeader(payload: Int, rate: Int, channels: Int): ByteArray {
        val rates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
        val freq = rates.indexOf(rate).let { if (it < 0) 4 else it }
        val len = payload + 7
        return byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(),
            (((2 - 1) shl 6) or (freq shl 2) or (channels shr 2)).toByte(),
            (((channels and 3) shl 6) or (len shr 11)).toByte(),
            ((len and 0x7FF) shr 3).toByte(),
            (((len and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte()
        )
    }

    private fun stopStreaming() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) {}
        try { encoder?.stop() } catch (_: Exception) {}
        try { connection?.disconnect() } catch (_: Exception) {}
        recorder = null; encoder = null; connection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Status streaming", NotificationManager.IMPORTANCE_LOW))
        }
    }
    private fun notification(text: String): Notification {
        val b = if (android.os.Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setContentTitle("Dewi Anjani Live").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build()
    }
}
