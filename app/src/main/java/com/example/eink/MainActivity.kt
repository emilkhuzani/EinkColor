package com.example.eink

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val p = Params().also { it.set(EInk.KALEIDO3) }
    private val io = Executors.newSingleThreadExecutor()
    private var enabled = false
    private lateinit var status: TextView
    private lateinit var preview: PreviewView

    private class Slider(val bar: SeekBar, val get: () -> Float, val set: (Float) -> Unit,
                         val toProgress: (Float) -> Int)
    private val sliders = mutableListOf<Slider>()

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refreshStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(TextView(this).apply { text = "E-Ink Color"; textSize = 26f; setTextColor(Color.BLACK) })
        status = TextView(this).apply { textSize = 13f; setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(status)

        root.addView(label("Pratinjau (atas: asli, bawah: e-ink)"))
        preview = PreviewView(this, p)
        root.addView(preview)

        root.addView(row(
            button("Aktifkan") { enabled = true; push() },
            button("Matikan") { enabled = false; push() }
        ))

        root.addView(label("Preset"))
        root.addView(row(
            button("Kaleido 3") { preset(EInk.KALEIDO3) },
            button("Gallery 3") { preset(EInk.GALLERY3) },
            button("Mono") { preset(EInk.MONO) }
        ))

        addSlider(root, "Saturasi warna", { p.saturation }, { p.saturation = it }, 0f, 1f)
        addSlider(root, "Kehangatan kertas", { p.warmth }, { p.warmth = it }, 0f, 1f)
        addSlider(root, "Level hitam (kontras)", { p.blackLift }, { p.blackLift = it }, 0f, 0.20f)
        addSlider(root, "Putih kertas (kecerahan)", { p.whitePoint }, { p.whitePoint = it }, 0.60f, 1f)

        root.addView(label("Tanpa Shizuku / root"))
        root.addView(row(
            button("Izin Shizuku") { requestShizuku() },
            button("Salin perintah ADB") { copyAdb() }
        ))

        Shizuku.addRequestPermissionResultListener(shizukuListener)
        refreshStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    private fun preset(x: Params) {
        p.set(x)
        sliders.forEach { it.bar.progress = it.toProgress(it.get()) }
        preview.invalidate()
        if (enabled) push()
    }

    private fun push() {
        val cmd = if (enabled) EInk.applyCommand(p) else EInk.RESET_COMMAND
        io.execute {
            val backend = Shell.run(cmd)
            runOnUiThread {
                if (backend == null) {
                    toast("Gagal. Aktifkan Shizuku/root, atau pakai perintah ADB.")
                    refreshStatus()
                } else status.text = "Aktif lewat $backend: " + if (enabled) "e-ink ON" else "e-ink OFF"
            }
        }
    }

    private fun refreshStatus() {
        status.text = when {
            Shell.shizukuReady() -> "Shizuku siap."
            Shell.shizukuRunning() -> "Shizuku berjalan, tapi izin belum diberikan. Tekan 'Izin Shizuku'."
            else -> "Shizuku tidak aktif. Akan dicoba root; atau salin perintah ADB lalu jalankan di PC."
        }
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) toast("Jalankan aplikasi Shizuku dulu")
            else if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) toast("Izin sudah ada")
            else Shizuku.requestPermission(1)
        } catch (_: Throwable) { toast("Shizuku tidak tersedia") }
    }

    private fun copyAdb() {
        val cmd = "adb shell " + (if (enabled) EInk.applyCommand(p) else EInk.applyCommand(p))
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("adb", cmd))
        toast("Perintah disalin. Reset: adb shell ${EInk.RESET_COMMAND}")
    }

    // ---------- UI helpers ----------
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(Color.BLACK); setPadding(0, 28, 0, 8)
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun row(vararg b: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        b.forEach { addView(it) }
    }

    private fun addSlider(parent: LinearLayout, name: String, get: () -> Float, set: (Float) -> Unit,
                          min: Float, max: Float) {
        parent.addView(label(name))
        val toProgress = { v: Float -> (((v - min) / (max - min)) * 100f).toInt().coerceIn(0, 100) }
        val bar = SeekBar(this).apply {
            this.max = 100
            progress = toProgress(get())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    set(min + (max - min) * v / 100f)
                    preview.invalidate()
                    if (enabled) push()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        sliders.add(Slider(bar, get, set, toProgress))
        parent.addView(bar)
    }
}
