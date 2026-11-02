package com.example.eink

import rikka.shizuku.Shizuku

object Shell {
    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun shizukuRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    private fun waitFor(p: Process): Boolean = p.waitFor() == 0

    private fun viaShizuku(cmd: String): Boolean {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        return waitFor(p)
    }

    private fun viaRoot(cmd: String): Boolean =
        waitFor(ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start())

    /** Jalankan perintah dengan hak shell: Shizuku dulu, lalu root. Return nama backend atau null. */
    fun run(cmd: String): String? {
        if (shizukuReady()) {
            try { if (viaShizuku(cmd)) return "Shizuku" } catch (_: Throwable) {}
        }
        try { if (viaRoot(cmd)) return "Root" } catch (_: Throwable) {}
        return null
    }
}
