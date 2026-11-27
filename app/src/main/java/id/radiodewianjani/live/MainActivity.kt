package id.radiodewianjani.live

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var mount: EditText
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        fun field(label: String, value: String = "", secret: Boolean = false): EditText {
            val e = EditText(this).apply {
                hint = label
                if (secret) inputType = 129
                if (value.isNotEmpty()) setText(value)
            }
            layout.addView(e, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return e
        }
        layout.addView(TextView(this).apply {
            text = "DEWI ANJANI LIVE — Icecast"
            textSize = 24f
        })
        host = field("Alamat server Icecast")
        port = field("Port", "8000")
        mount = field("Mount point", "/live.aac")
        user = field("Username source", "source")
        pass = field("Password source", secret = true)
        status = TextView(this).apply { text = "Siap"; textSize = 16f }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "MULAI SIARAN"
            setOnClickListener { startBroadcast() }
        })
        layout.addView(Button(this).apply {
            text = "HENTIKAN SIARAN"
            setOnClickListener {
                startService(Intent(this@MainActivity, StreamService::class.java).setAction(StreamService.ACTION_STOP))
                status.text = "Perintah berhenti dikirim."
            }
        })
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun startBroadcast() {
        if (host.text.isNullOrBlank() || port.text.isNullOrBlank() ||
            mount.text.isNullOrBlank() || user.text.isNullOrBlank() || pass.text.isNullOrBlank()) {
            status.text = "Lengkapi semua pengaturan server."
            return
        }
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            status.text = "Izinkan mikrofon, lalu tekan Mulai Siaran lagi."
            return
        }
        val intent = Intent(this, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra("host", host.text.toString().trim())
            putExtra("port", port.text.toString().toIntOrNull() ?: 8000)
            putExtra("mount", mount.text.toString().trim())
            putExtra("user", user.text.toString())
            putExtra("pass", pass.text.toString())
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "Memulai streaming…"
    }
}
