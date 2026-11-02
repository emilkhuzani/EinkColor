package com.example.eink

import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.math.roundToInt

/**
 * Mode sistem: tidak memakai SurfaceFlinger 1015 (diblokir di sebagian HP).
 * Saturasi diatur lewat ColorDisplayService (dipanggil dengan hak shell lewat Shizuku),
 * kehangatan lewat Night Light, dan putih kertas lewat "Reduce bright colors".
 */
object SystemMode {
    private var bypassed = false

    private fun root(t: Throwable): String {
        var e: Throwable = t
        while (e.cause != null && e.cause !== e) e = e.cause!!
        return e.javaClass.simpleName + ": " + (e.message ?: "")
    }

    private fun setSaturation(level: Int) {
        if (!bypassed) {
            try { HiddenApiBypass.addHiddenApiExemptions("L") } catch (_: Throwable) {}
            bypassed = true
        }
        val binder: IBinder = SystemServiceHelper.getSystemService("color_display")
            ?: error("layanan color_display tidak ada di HP ini")
        val wrapped = ShizukuBinderWrapper(binder)
        val stub = Class.forName("android.hardware.display.IColorDisplayManager\$Stub")
        val iface = stub.getMethod("asInterface", IBinder::class.java).invoke(null, wrapped)!!
        iface.javaClass.getMethod("setSaturationLevel", Int::class.javaPrimitiveType).invoke(iface, level)
    }

    fun apply(p: Params, on: Boolean): String {
        if (!Shell.shizukuReady()) return "Mode sistem butuh Shizuku aktif + izin diberikan."
        val sb = StringBuilder()
        var satOk = true
        try {
            setSaturation(if (on) (p.saturation * 100).roundToInt().coerceIn(0, 100) else 100)
            sb.append("Saturasi: OK")
        } catch (t: Throwable) {
            satOk = false
            sb.append("Saturasi gagal (").append(root(t)).append(")")
        }

        val cmd = if (on) {
            val k = (4082 - (4082 - 2596) * p.warmth).roundToInt()
            val rb = (((1f - p.whitePoint) / 0.4f) * 100).roundToInt().coerceIn(0, 100)
            val gray = !satOk && p.saturation < 0.2f
            listOf(
                if (p.warmth > 0.05f)
                    "settings put secure night_display_auto_mode 0; " +
                    "settings put secure night_display_color_temperature $k; " +
                    "settings put secure night_display_activated 1"
                else "settings put secure night_display_activated 0",
                "settings put secure reduce_bright_colors_level $rb",
                "settings put secure reduce_bright_colors_activated ${if (rb > 0) 1 else 0}",
                if (gray) "settings put secure accessibility_display_daltonizer 0; " +
                    "settings put secure accessibility_display_daltonizer_enabled 1" else ""
            ).filter { it.isNotEmpty() }.joinToString("; ")
        } else {
            "settings put secure night_display_activated 0; " +
            "settings put secure reduce_bright_colors_activated 0; " +
            "settings put secure accessibility_display_daltonizer_enabled 0"
        }
        val r = Shell.run(cmd)
        sb.append("\nKehangatan/putih kertas: ").append(if (r.backend != null) "OK" else "gagal " + r.out.take(120))
        sb.append("\nCatatan: level hitam tidak bisa diatur di mode ini.")
        return sb.toString()
    }
}
