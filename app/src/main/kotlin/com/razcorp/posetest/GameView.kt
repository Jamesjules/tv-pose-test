package com.razcorp.posetest

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.hypot
import kotlin.random.Random

/** Demo game: pop the bubbles with your hands. Input = skeleton from phone OR the TV's own camera. */
class GameView(ctx: Context) : View(ctx) {
    @Volatile private var pose: FloatArray? = null
    @Volatile private var thumb: Bitmap? = null
    @Volatile var info: String = ""

    private class Bubble(val x: Float, val y: Float, val born: Long)
    private val bubbles = ArrayList<Bubble>()
    private var score = 0
    private var lastSpawn = 0L

    private val bg = Paint().apply { color = Color.rgb(16, 24, 32) }
    private val bub = Paint().apply { color = Color.rgb(0, 200, 255); isAntiAlias = true }
    private val bubEdge = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val line = Paint().apply { color = Color.GREEN; strokeWidth = 8f; isAntiAlias = true }
    private val hand = Paint().apply { color = Color.YELLOW; isAntiAlias = true }
    private val big = Paint().apply { color = Color.WHITE; textSize = 90f; isAntiAlias = true; setShadowLayer(6f, 3f, 3f, Color.BLACK) }
    private val small = Paint().apply { color = Color.LTGRAY; textSize = 28f; isAntiAlias = true; setShadowLayer(4f, 2f, 2f, Color.BLACK) }
    private val bones = intArrayOf(11,12, 11,13, 13,15, 12,14, 14,16, 11,23, 12,24, 23,24, 23,25, 25,27, 24,26, 26,28)
    private val handIdx = intArrayOf(15, 16, 19, 20)

    fun setPose(p: FloatArray?) { pose = p }
    fun setThumb(b: Bitmap) { thumb = b }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRect(0f, 0f, w, h, bg)
        val now = SystemClock.elapsedRealtime()
        val r = minOf(w, h) * 0.08f

        if (now - lastSpawn > 900 && bubbles.size < 6) {
            bubbles.add(Bubble(0.1f + Random.nextFloat() * 0.8f, 0.15f + Random.nextFloat() * 0.7f, now)); lastSpawn = now
        }
        bubbles.removeAll { now - it.born > 5000 }

        val p = pose
        val hands = ArrayList<PointF>()
        if (p != null) {
            fun sx(i: Int) = (1f - p[i * 2]) * w          // mirrored, like a mirror
            fun sy(i: Int) = p[i * 2 + 1] * h
            val n = p.size / 2
            var k = 0
            while (k < bones.size) {
                val a = bones[k]; val d = bones[k + 1]
                if (a < n && d < n) c.drawLine(sx(a), sy(a), sx(d), sy(d), line)
                k += 2
            }
            for (i in handIdx) if (i < n) { hands.add(PointF(sx(i), sy(i))); c.drawCircle(sx(i), sy(i), 16f, hand) }
        }

        val it = bubbles.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val bx = b.x * w; val by = b.y * h
            if (hands.any { hypot(it.x - bx, it.y - by) < r * 1.2f }) { score++; it.remove(); continue }
            c.drawCircle(bx, by, r, bub); c.drawCircle(bx, by, r, bubEdge)
        }

        c.drawText("Score: $score", 40f, h - 40f, big)
        var ty = 40f
        for (l in info.split('\n')) { c.drawText(l, 30f, ty, small); ty += 34f }
        thumb?.let { c.drawBitmap(it, null, RectF(w - 260f, 20f, w - 20f, 20f + 180f), null) }
        postInvalidateOnAnimation()
    }
}
