package com.razcorp.posetest

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun withAlpha(c: Int, a: Int): Int = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
fun lighten(c: Int, f: Float): Int = Color.rgb(
    (Color.red(c) + (255 - Color.red(c)) * f).toInt(), (Color.green(c) + (255 - Color.green(c)) * f).toInt(), (Color.blue(c) + (255 - Color.blue(c)) * f).toInt())
fun darken(c: Int, f: Float): Int = Color.rgb((Color.red(c) * (1f - f)).toInt(), (Color.green(c) * (1f - f)).toInt(), (Color.blue(c) * (1f - f)).toInt())

/** Visual effects shared by all games (UI thread only). */
object Fx {
    private class P(var x: Float, var y: Float, val vx: Float, var vy: Float, var life: Float, val maxLife: Float, val color: Int, val size: Float, val grav: Float)
    private class T(val x: Float, var y: Float, val text: String, val color: Int, var life: Float, val big: Boolean)
    private val ps = ArrayList<P>()
    private val ts = ArrayList<T>()
    private var shakeLeft = 0f
    private var shakeAmp = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(6f, 2f, 2f, Color.BLACK) }

    fun clear() { ps.clear(); ts.clear(); shakeLeft = 0f }

    fun burst(x: Float, y: Float, color: Int, n: Int = 14, speed: Float = 0.5f, size: Float = 8f) {
        repeat(n) {
            if (ps.size < 260) {
                val a = Random.nextFloat() * 6.2832f
                val sp = speed * (0.3f + 0.7f * Random.nextFloat())
                val life = 400f + 400f * Random.nextFloat()
                ps.add(P(x, y, cos(a) * sp, sin(a) * sp, life, life, color, size * (0.5f + Random.nextFloat()), 0.0012f))
            }
        }
    }

    fun text(x: Float, y: Float, text: String, color: Int, big: Boolean = false) { if (ts.size < 40) ts.add(T(x, y, text, color, 800f, big)) }
    fun shake(ms: Float, amp: Float) { shakeLeft = ms; shakeAmp = amp }

    fun confetti(w: Float, h: Float, n: Int = 90) {
        repeat(n) {
            val col = Color.HSVToColor(floatArrayOf(Random.nextFloat() * 360f, 0.8f, 1f))
            ps.add(P(Random.nextFloat() * w, -20f - Random.nextFloat() * h * 0.3f, (Random.nextFloat() - 0.5f) * 0.2f,
                0.15f + 0.2f * Random.nextFloat(), 3500f, 3500f, col, 8f + 8f * Random.nextFloat(), 0.00005f))
        }
    }

    fun update(dt: Long) {
        val d = dt.toFloat()
        for (p in ps) { p.x += p.vx * d; p.y += p.vy * d; p.vy += p.grav * d; p.life -= d }
        ps.removeAll { it.life <= 0f }
        for (t in ts) { t.y -= 0.06f * d; t.life -= d }
        ts.removeAll { it.life <= 0f }
        if (shakeLeft > 0f) shakeLeft -= d
    }

    fun applyShake(c: Canvas) {
        if (shakeLeft > 0f) {
            val k = (shakeLeft / 300f).coerceAtMost(1f)
            c.translate((Random.nextFloat() - 0.5f) * 2f * shakeAmp * k, (Random.nextFloat() - 0.5f) * 2f * shakeAmp * k)
        }
    }

    fun draw(c: Canvas, h: Float) {
        for (p in ps) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            paint.color = withAlpha(p.color, (255 * a).toInt())
            c.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * a), paint)
        }
        for (t in ts) {
            val a = (t.life / 800f).coerceIn(0f, 1f)
            tp.textSize = h * (if (t.big) 0.075f else 0.05f)
            tp.color = withAlpha(t.color, (255 * a).toInt())
            c.drawText(t.text, t.x, t.y, tp)
        }
    }
}
