package com.razcorp.posetest

import android.content.Context
import android.graphics.*
import android.view.View

class OverlayView(ctx: Context) : View(ctx) {
    @Volatile private var frame: Bitmap? = null
    @Volatile private var poses: List<FloatArray> = emptyList()
    @Volatile var info: String = ""

    private val line = Paint().apply { strokeWidth = 6f; isAntiAlias = true }
    private val dot = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val text = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true; setShadowLayer(4f, 2f, 2f, Color.BLACK) }
    private val bones = intArrayOf(11,12, 11,13, 13,15, 12,14, 14,16, 11,23, 12,24, 23,24, 23,25, 25,27, 24,26, 26,28)

    fun setFrame(b: Bitmap) { frame = b; postInvalidate() }
    fun setPoses(p: List<FloatArray>) { poses = p; postInvalidate() }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val b = frame
        var dst = RectF(0f, 0f, width.toFloat(), height.toFloat())
        if (b != null) {
            val s = minOf(width / b.width.toFloat(), height / b.height.toFloat())
            val w = b.width * s; val h = b.height * s
            dst = RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            c.drawBitmap(b, null, dst, null)
        }
        for ((pi, p) in poses.withIndex()) {
            line.color = PLAYER_COLORS[pi % 4]
            fun x(i: Int) = dst.left + p[i * 2] * dst.width()
            fun y(i: Int) = dst.top + p[i * 2 + 1] * dst.height()
            var k = 0
            while (k < bones.size) {
                val a = bones[k]; val d = bones[k + 1]
                if (a * 2 + 1 < p.size && d * 2 + 1 < p.size) c.drawLine(x(a), y(a), x(d), y(d), line)
                k += 2
            }
            for (i in 0 until p.size / 2) c.drawCircle(x(i), y(i), 6f, dot)
        }
        var ty = 50f
        for (l in info.split('\n')) { c.drawText(l, 30f, ty, text); ty += 36f }
    }
}
