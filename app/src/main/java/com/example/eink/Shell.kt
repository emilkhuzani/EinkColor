package com.example.eink

import rikka.shizuku.Shizuku

data class Res(val backend: String?, val out: String)

object Shell {
    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun shizukuRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    private fun collect(p: Process): Pair<Boolean, String> {
        val out = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        return Pair(p.waitFor() == 0, out.trim())
    }

    private fun viaShizuku(cmd: String): Pair<Boolean, String> {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        return collect(m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process)
    }

    private fun viaRoot(cmd: String): Pair<Boolean, String> =
        collect(ProcessBuilder("su", "-c", cmd).start())

    /** Jalankan perintah dengan hak shell: Shizuku dulu, lalu root. Mengembalikan backend + output asli. */
    fun run(cmd: String): Res {
        var last = ""
        if (shizukuReady()) {
            try { val (ok, out) = viaShizuku(cmd); if (ok) return Res("Shizuku", out); last = out }
            catch (e: Throwable) { last = e.toString() }
        }
        try { val (ok, out) = viaRoot(cmd); if (ok) return Res("Root", out); last = out }
        catch (e: Throwable) { if (last.isEmpty()) last = e.toString() }
        return Res(null, last)
    }
}
