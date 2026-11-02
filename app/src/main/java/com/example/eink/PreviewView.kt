package com.example.eink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Baris atas = warna asli, baris bawah = hasil tampilan e-ink (dihitung dengan matriks yang sama). */
class PreviewView(ctx: Context, private val p: Params) : View(ctx) {
    private val paint = Paint()
    private val colors = intArrayOf(
        0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
        0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFD9A58B.toInt(),
        0xFFFFFFFF.toInt(), 0xFF808080.toInt(), 0xFF000000.toInt()
    )

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (140 * resources.displayMetrics.density).toInt())
    }

    private fun transform(c: Int, m: FloatArray): Int {
        val r = Color.red(c) / 255f; val g = Color.green(c) / 255f; val b = Color.blue(c) / 255f
        fun ch(row: Int) = (m[row] * r + m[4 + row] * g + m[8 + row] * b + m[12 + row]).coerceIn(0f, 1f)
        return Color.rgb((ch(0) * 255).toInt(), (ch(1) * 255).toInt(), (ch(2) * 255).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val m = EInk.matrix(p)
        val cw = width / colors.size.toFloat()
        val half = height / 2f
        for (i in colors.indices) {
            paint.color = colors[i]
            canvas.drawRect(i * cw, 0f, (i + 1) * cw, half, paint)
            paint.color = transform(colors[i], m)
            canvas.drawRect(i * cw, half, (i + 1) * cw, height.toFloat(), paint)
        }
    }
}
