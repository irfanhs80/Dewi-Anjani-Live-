package id.radiodewianjani.live

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
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
        fun field(hintText: String, secret: Boolean = false): EditText {
            return EditText(this).apply {
                hint = hintText
                if (secret) inputType = 129
                layout.addView(this)
            }
        }
        layout.addView(TextView(this).apply {
            text = "DEWI ANJANI LIVE — Icecast"
            textSize = 22f
        })
        host = field("Alamat server (contoh: radio.example.id)")
        port = field("Port (contoh: 8000)").apply { setText("8000") }
        mount = field("Mount point (contoh: /live.aac)").apply { setText("/live.aac") }
        user = field("Username source")
        pass = field("Password source", true)
        status = TextView(this).apply { text = "Siap"; textSize = 16f; layout.addView(this) }
        layout.addView(Button(this).apply {
            text = "MULAI SIARAN"
            setOnClickListener {
                if (host.text.isNullOrBlank() || port.text.isNullOrBlank() ||
                    mount.text.isNullOrBlank() || user.text.isNullOrBlank() || pass.text.isNullOrBlank()) {
                    status.text = "Lengkapi semua pengaturan server."
                    return@setOnClickListener
                }
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
                    status.text = "Izinkan mikrofon, lalu tekan Mulai Siaran lagi."
                    return@setOnClickListener
                }
                val i = Intent(this@MainActivity, StreamService::class.java).apply {
                    action = StreamService.ACTION_START
                    putExtra("host", host.text.toString().trim())
                    putExtra("port", port.text.toString().toIntOrNull() ?: 8000)
                    putExtra("mount", mount.text.toString().trim())
                    putExtra("user", user.text.toString())
                    putExtra("pass", pass.text.toString())
                }
                ContextCompat.startForegroundService(this@MainActivity, i)
                status.text = "Memulai streaming…"
            }
        })
        layout.addView(Button(this).apply {
            text = "HENTIKAN SIARAN"
            setOnClickListener {
                startService(Intent(this@MainActivity, StreamService::class.java).setAction(StreamService.ACTION_STOP))
                status.text = "Siaran dihentikan."
            }
        })
        setContentView(ScrollView(this).apply { addView(layout) })
    }
}
