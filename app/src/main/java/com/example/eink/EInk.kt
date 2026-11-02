package com.example.eink

import java.util.Locale

/** Parameter tampilan e-ink. */
data class Params(
    var saturation: Float = 0.40f,  // 0 = hitam-putih, 1 = warna LCD penuh
    var warmth: Float = 0.50f,      // 0 = putih netral, 1 = kertas kekuningan
    var blackLift: Float = 0.07f,   // hitam e-ink sebenarnya abu gelap, bukan 0
    var whitePoint: Float = 0.90f   // putih e-ink sebenarnya abu terang, bukan 1
) {
    fun set(o: Params) {
        saturation = o.saturation; warmth = o.warmth
        blackLift = o.blackLift; whitePoint = o.whitePoint
    }
}

object EInk {
    val KALEIDO3 = Params(0.40f, 0.50f, 0.07f, 0.90f)
    val GALLERY3 = Params(0.65f, 0.35f, 0.06f, 0.92f)
    val MONO = Params(0.00f, 0.40f, 0.07f, 0.90f)

    /**
     * Matriks warna 4x4 (column-major, format SurfaceFlinger/GLES).
     * out = M * [r g b 1]
     *  1) desaturasi (luma Rec.709)
     *  2) skala ke "putih kertas" (whitePoint + tint hangat)
     *  3) angkat level hitam (blackLift)
     */
    fun matrix(p: Params): FloatArray {
        val lr = 0.2126f; val lg = 0.7152f; val lb = 0.0722f
        val s = p.saturation
        val sat = arrayOf(
            floatArrayOf(lr * (1 - s) + s, lg * (1 - s), lb * (1 - s)),
            floatArrayOf(lr * (1 - s), lg * (1 - s) + s, lb * (1 - s)),
            floatArrayOf(lr * (1 - s), lg * (1 - s), lb * (1 - s) + s)
        )
        val w = p.whitePoint
        val white = floatArrayOf(w, w * (1 - 0.03f * p.warmth), w * (1 - 0.14f * p.warmth))
        val lift = p.blackLift

        val out = FloatArray(16)
        for (col in 0..2) {
            for (row in 0..2) out[col * 4 + row] = (white[row] - lift) * sat[row][col]
            out[col * 4 + 3] = 0f
        }
        for (row in 0..2) out[12 + row] = lift
        out[15] = 1f
        return out
    }

    fun applyCommand(p: Params): String {
        val m = matrix(p)
        val sb = StringBuilder("service call SurfaceFlinger 1015 i32 1")
        for (f in m) sb.append(" f ").append(String.format(Locale.US, "%.4f", f))
        return sb.toString()
    }

    const val RESET_COMMAND = "service call SurfaceFlinger 1015 i32 0"
}
