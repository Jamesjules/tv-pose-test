package com.razcorp.posetest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val HANDS = intArrayOf(15, 16, 19, 20)
private val quizDeck = ArrayList<Int>()
private fun fillP() = Paint(Paint.ANTI_ALIAS_FLAG)
private fun lineP() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.WHITE }
private fun textP() = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

private fun smoothCursor(old: PointF?, target: PointF?): PointF? {
    if (target == null) return null
    if (old == null) return PointF(target.x, target.y)
    return PointF(old.x + (target.x - old.x) * 0.5f, old.y + (target.y - old.y) * 0.5f)
}

private fun drawCursorDot(c: Canvas, p: Paint, pt: PointF, col: Int, h: Float) {
    p.style = Paint.Style.FILL
    p.color = withAlpha(col, 90); c.drawCircle(pt.x, pt.y, h * 0.035f, p)
    p.color = lighten(col, 0.5f); c.drawCircle(pt.x, pt.y, h * 0.015f, p)
}

// ======================= 7. Memory Match =======================
class MemoryGame : Game("memory", "Memory Match", "Flip two cards to find pairs. Take turns!") {
    override val hint = "Raise a hand - hover a card to flip it"
    override val color = Color.rgb(235, 110, 70)
    override val bgTop = Color.rgb(40, 16, 24)
    override val bgBottom = Color.rgb(120, 40, 60)
    override val roundMs = 240000L
    override val joinLate = false
    override fun status(slot: Int): String = if (joined[slot]) "${scores[slot]} pairs" else ""

    private val ids = IntArray(16)
    private val faceUp = BooleanArray(16)
    private val matched = BooleanArray(16)
    private var first = -1
    private var second = -1
    private var hideAt = 0L
    private var turnIdx = 0
    private var turnStart = 0L
    private var pairsLeft = 8
    private var hoverCell = -1
    private var hoverStart = 0L
    private var cursor: PointF? = null
    private val slots = ArrayList<Int>()
    private val paint = fillP()
    private val stroke = lineP().apply { strokeWidth = 5f }
    private val txt = textP()
    private val path = Path()

    private fun curSlot() = if (slots.isEmpty()) -1 else slots[turnIdx % slots.size]

    private fun geom(w: Float, h: Float): FloatArray {
        val cs = min(h * 0.16f, w * 0.11f); val gap = cs * 0.12f
        val total = 4 * cs + 3 * gap
        return floatArrayOf(cs, gap, (w - total) / 2f, h * 0.25f, total)
    }

    override fun onStart(now: Long) {
        slots.clear(); for (i in 0 until 4) if (joined[i]) slots.add(i)
        val list = ArrayList<Int>(); for (i in 0 until 8) { list.add(i); list.add(i) }
        list.shuffle()
        for (i in 0 until 16) { ids[i] = list[i]; faceUp[i] = false; matched[i] = false }
        first = -1; second = -1; hideAt = 0L; turnIdx = 0; turnStart = now; pairsLeft = 8; hoverCell = -1; cursor = null
    }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        if (slots.isEmpty()) return
        if (hideAt != 0L) {
            if (now >= hideAt) {
                faceUp[first] = false; faceUp[second] = false; first = -1; second = -1; hideAt = 0L
                turnIdx++; turnStart = now; hoverCell = -1
            }
            return
        }
        val s = curSlot()
        if (now - turnStart > 20000L && first == -1) { turnIdx++; turnStart = now; return }
        val g = geom(w, h)
        val cs = g[0]; val gap = g[1]; val bx = g[2]; val by = g[3]; val total = g[4]
        val area = RectF(bx - cs * 0.5f, by - cs * 0.3f, bx + total + cs * 0.5f, by + total + cs * 0.3f)
        val p = players.firstOrNull { it.slot == s }
        cursor = smoothCursor(cursor, if (p != null) cursorOf(p, area) else null)
        val cu = cursor
        if (cu == null) { hoverCell = -1; return }
        val col = ((cu.x - bx) / (cs + gap)).toInt()
        val row = ((cu.y - by) / (cs + gap)).toInt()
        var cell = -1
        if (col in 0..3 && row in 0..3) {
            val inX = cu.x - bx - col * (cs + gap) <= cs
            val inY = cu.y - by - row * (cs + gap) <= cs
            if (inX && inY) cell = row * 4 + col
        }
        if (cell != hoverCell) { hoverCell = cell; hoverStart = now }
        if (cell >= 0 && !faceUp[cell] && !matched[cell] && now - hoverStart >= 500L) {
            faceUp[cell] = true; Sfx.tick(); hoverCell = -1
            if (first == -1) first = cell
            else {
                second = cell
                if (ids[first] == ids[second]) {
                    matched[first] = true; matched[second] = true; scores[s]++; Sfx.pop(); pairsLeft--
                    for (k in intArrayOf(first, second)) Fx.burst(bx + (k % 4) * (cs + gap) + cs / 2, by + (k / 4) * (cs + gap) + cs / 2, PLAYER_COLORS[s], 12, 0.4f, h * 0.008f)
                    first = -1; second = -1; turnStart = now
                    if (pairsLeft == 0) finish()
                } else hideAt = now + 900L
            }
        }
    }

    private fun symbol(c: Canvas, id: Int, cx: Float, cy: Float, s: Float) {
        when (id) {
            0 -> { paint.color = Color.rgb(240, 70, 80); c.drawCircle(cx, cy, s * 0.4f, paint) }
            1 -> { paint.color = Color.rgb(70, 130, 240); c.drawRoundRect(RectF(cx - s * 0.38f, cy - s * 0.38f, cx + s * 0.38f, cy + s * 0.38f), s * 0.08f, s * 0.08f, paint) }
            2 -> { paint.color = Color.rgb(70, 200, 110); path.reset(); path.moveTo(cx, cy - s * 0.42f); path.lineTo(cx + s * 0.42f, cy + s * 0.34f); path.lineTo(cx - s * 0.42f, cy + s * 0.34f); path.close(); c.drawPath(path, paint) }
            3 -> { paint.color = Color.rgb(255, 210, 60); path.reset()
                   for (k in 0 until 10) { val r = if (k % 2 == 0) s * 0.45f else s * 0.2f; val a = -1.5708f + k * 0.62832f
                       val x = cx + cos(a) * r; val y = cy + sin(a) * r; if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                   path.close(); c.drawPath(path, paint) }
            4 -> { paint.color = Color.rgb(255, 100, 170); drawHeart(c, cx, cy - s * 0.02f, s * 0.3f, paint) }
            5 -> { paint.color = Color.rgb(70, 210, 220); path.reset(); path.moveTo(cx, cy - s * 0.45f); path.lineTo(cx + s * 0.34f, cy); path.lineTo(cx, cy + s * 0.45f); path.lineTo(cx - s * 0.34f, cy); path.close(); c.drawPath(path, paint) }
            6 -> { paint.color = Color.rgb(255, 150, 50); c.drawRect(cx - s * 0.13f, cy - s * 0.4f, cx + s * 0.13f, cy + s * 0.4f, paint); c.drawRect(cx - s * 0.4f, cy - s * 0.13f, cx + s * 0.4f, cy + s * 0.13f, paint) }
            else -> { stroke.color = Color.rgb(170, 100, 240); stroke.strokeWidth = s * 0.14f; c.drawCircle(cx, cy, s * 0.3f, stroke) }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        if (slots.isEmpty()) return
        val now = GameClock.now()
        val g = geom(w, h); val cs = g[0]; val gap = g[1]; val bx = g[2]; val by = g[3]
        for (i in 0 until 16) {
            val x = bx + (i % 4) * (cs + gap); val y = by + (i / 4) * (cs + gap)
            val r = RectF(x, y, x + cs, y + cs)
            if (faceUp[i] || matched[i]) {
                paint.color = if (matched[i]) Color.rgb(225, 250, 225) else Color.rgb(250, 248, 240)
                c.drawRoundRect(r, cs * 0.12f, cs * 0.12f, paint)
                symbol(c, ids[i], r.centerX(), r.centerY(), cs)
                if (matched[i]) { stroke.color = Color.rgb(80, 220, 120); stroke.strokeWidth = 5f; c.drawRoundRect(r, cs * 0.12f, cs * 0.12f, stroke) }
            } else {
                paint.shader = LinearGradient(r.left, r.top, r.right, r.bottom, Color.rgb(120, 80, 200), Color.rgb(70, 40, 140), Shader.TileMode.CLAMP)
                c.drawRoundRect(r, cs * 0.12f, cs * 0.12f, paint); paint.shader = null
                paint.color = withAlpha(Color.WHITE, 60); c.drawCircle(r.centerX(), r.centerY(), cs * 0.22f, paint)
                drawCenterText(c, txt, "?", r.centerX(), r.centerY() + cs * 0.14f, cs * 0.4f, withAlpha(Color.WHITE, 150))
                if (i == hoverCell) {
                    paint.color = withAlpha(Color.WHITE, 60); c.drawRoundRect(r, cs * 0.12f, cs * 0.12f, paint)
                    val pr = ((now - hoverStart) / 500f).coerceIn(0f, 1f)
                    paint.color = Color.WHITE; c.drawRect(r.left + 8f, r.bottom - 14f, r.left + 8f + (cs - 16f) * pr, r.bottom - 6f, paint)
                }
            }
        }
        val s = curSlot()
        if (s >= 0) {
            drawCenterText(c, txt, "${PLAYER_NAMES[s]}'s turn", w / 2, by - h * 0.04f, h * 0.05f, PLAYER_COLORS[s])
            cursor?.let { drawCursorDot(c, paint, it, PLAYER_COLORS[s], h) }
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        paint.color = Color.rgb(100, 60, 180); c.drawRoundRect(RectF(cx - s * 0.95f, cy - s * 0.6f, cx - s * 0.1f, cy + s * 0.7f), s * 0.15f, s * 0.15f, paint)
        paint.color = Color.rgb(250, 248, 240); c.drawRoundRect(RectF(cx + s * 0.1f, cy - s * 0.6f, cx + s * 0.95f, cy + s * 0.7f), s * 0.15f, s * 0.15f, paint)
        symbol(c, 3, cx + s * 0.52f, cy + s * 0.05f, s * 1.1f)
        drawCenterText(c, txt, "?", cx - s * 0.52f, cy + s * 0.25f, s * 0.8f, withAlpha(Color.WHITE, 200))
    }
}

// ======================= 8. Whack-a-Mole =======================
class MoleGame : Game("mole", "Whack-a-Mole", "Whack the moles with your hands. Careful with the bomb moles!") {
    override val hint = "Whack the moles!"
    override val color = Color.rgb(160, 105, 60)
    override val bgTop = Color.rgb(22, 52, 26)
    override val bgBottom = Color.rgb(52, 112, 52)
    private class Mo(val hole: Int, val born: Long, val dur: Long, val type: Int)
    private val moles = ArrayList<Mo>()
    private var lastSpawn = 0L
    private val paint = fillP()
    private val stroke = lineP().apply { strokeWidth = 6f }

    private fun hx(i: Int, w: Float) = w * (0.16f + 0.226f * (i % 4))
    private fun hy(i: Int, h: Float) = h * (0.46f + 0.28f * (i / 4))

    override fun onReset(now: Long) { moles.clear(); lastSpawn = 0L }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        val n = max(1, players.size)
        val gap = (700f / (1f + 0.3f * (n - 1))).toLong()
        if (now - lastSpawn > gap && moles.size < 2 + n) {
            val free = (0 until 8).filter { hole -> moles.none { it.hole == hole } }
            if (free.isNotEmpty()) {
                val roll = Random.nextFloat()
                val type = if (roll < 0.1f) 1 else if (roll < 0.24f) 2 else 0
                moles.add(Mo(free[Random.nextInt(free.size)], now, if (type == 1) 900L else 1400L, type))
                lastSpawn = now
            }
        }
        moles.removeAll { now - it.born > it.dur }
        val r = min(w, h) * 0.085f
        val iter = moles.iterator()
        loop@ while (iter.hasNext()) {
            val m = iter.next()
            val age = now - m.born
            if (age < 120L) continue
            val mx = hx(m.hole, w); val my = hy(m.hole, h) - r * 0.7f
            for (p in players) for (i in HANDS) {
                if (i < p.n && hypot(p.x(i, w) - mx, p.y(i, h) - my) < r * 1.15f) {
                    val pts = when (m.type) { 1 -> 3; 2 -> -3; else -> 1 }
                    scores[p.slot] = max(0, scores[p.slot] + pts)
                    if (pts > 0) { Sfx.pop(); Fx.burst(mx, my, Color.rgb(200, 140, 80), 14, 0.45f, h * 0.008f) } else { Sfx.hit(); Fx.shake(250f, h * 0.01f); Fx.burst(mx, my, Color.rgb(255, 120, 30), 20, 0.6f, h * 0.01f) }
                    Fx.text(mx, my - r, if (pts > 0) "+$pts" else "$pts", if (pts > 0) PLAYER_COLORS[p.slot] else Color.RED, m.type != 0)
                    iter.remove(); continue@loop
                }
            }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = GameClock.now()
        val r = min(w, h) * 0.085f
        for (i in 0 until 8) {
            val x = hx(i, w); val y = hy(i, h)
            paint.color = Color.rgb(70, 48, 28); c.drawOval(RectF(x - r * 1.4f, y - r * 0.5f, x + r * 1.4f, y + r * 0.5f), paint)
            paint.color = Color.rgb(28, 18, 10); c.drawOval(RectF(x - r * 1.15f, y - r * 0.4f, x + r * 1.15f, y + r * 0.4f), paint)
        }
        for (m in moles) {
            val x = hx(m.hole, w); val y = hy(m.hole, h)
            val age = now - m.born
            val up = min(1f, age / 150f) * min(1f, (m.dur - age) / 150f)
            val by = y + r * 0.6f - up * r * 1.5f
            c.save(); c.clipRect(x - r * 1.6f, y - r * 3.5f, x + r * 1.6f, y + r * 0.05f)
            paint.color = when (m.type) { 1 -> Color.rgb(250, 200, 50); 2 -> Color.rgb(70, 70, 82); else -> Color.rgb(150, 100, 62) }
            c.drawCircle(x, by, r, paint)
            c.drawCircle(x - r * 0.8f, by - r * 0.7f, r * 0.28f, paint); c.drawCircle(x + r * 0.8f, by - r * 0.7f, r * 0.28f, paint)
            paint.color = if (m.type == 2) Color.rgb(100, 100, 114) else Color.rgb(205, 165, 125); c.drawOval(RectF(x - r * 0.55f, by + r * 0.05f, x + r * 0.55f, by + r * 0.95f), paint)
            paint.color = Color.WHITE; c.drawCircle(x - r * 0.35f, by - r * 0.2f, r * 0.2f, paint); c.drawCircle(x + r * 0.35f, by - r * 0.2f, r * 0.2f, paint)
            paint.color = if (m.type == 2) Color.RED else Color.rgb(25, 20, 20); c.drawCircle(x - r * 0.33f, by - r * 0.18f, r * 0.09f, paint); c.drawCircle(x + r * 0.37f, by - r * 0.18f, r * 0.09f, paint)
            paint.color = Color.rgb(240, 130, 150); c.drawCircle(x, by + r * 0.12f, r * 0.14f, paint)
            c.restore()
            stroke.color = Color.rgb(60, 40, 22); stroke.strokeWidth = r * 0.12f
            c.drawArc(RectF(x - r * 1.15f, y - r * 0.4f, x + r * 1.15f, y + r * 0.4f), 0f, 180f, false, stroke)
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        paint.color = Color.rgb(28, 18, 10); c.drawOval(RectF(cx - s * 0.9f, cy + s * 0.25f, cx + s * 0.9f, cy + s * 0.85f), paint)
        c.save(); c.clipRect(cx - s, cy - s, cx + s, cy + s * 0.55f)
        paint.color = Color.rgb(150, 100, 62); c.drawCircle(cx, cy + s * 0.15f, s * 0.6f, paint)
        paint.color = Color.WHITE; c.drawCircle(cx - s * 0.22f, cy, s * 0.13f, paint); c.drawCircle(cx + s * 0.22f, cy, s * 0.13f, paint)
        paint.color = Color.rgb(25, 20, 20); c.drawCircle(cx - s * 0.2f, cy + s * 0.01f, s * 0.06f, paint); c.drawCircle(cx + s * 0.24f, cy + s * 0.01f, s * 0.06f, paint)
        paint.color = Color.rgb(240, 130, 150); c.drawCircle(cx, cy + s * 0.2f, s * 0.09f, paint)
        c.restore()
    }
}

// ======================= 9. Fruit Catch (basket on your head) =======================
class CatchGame : Game("catch", "Fruit Catch", "A basket rides on your head. Move to catch fruit, dodge bombs!") {
    override val hint = "Move to catch the fruit"
    override val color = Color.rgb(235, 90, 125)
    override val bgTop = Color.rgb(40, 14, 52)
    override val bgBottom = Color.rgb(122, 42, 92)
    private class It(var x: Float, var y: Float, val vy: Float, val type: Int)
    private val items = ArrayList<It>()
    private val bx = FloatArray(4) { -1f }
    private val by = FloatArray(4) { -1f }
    private var lastSpawn = 0L
    private val cols = intArrayOf(Color.rgb(255, 140, 0), Color.rgb(230, 50, 60), Color.rgb(90, 200, 70), Color.rgb(250, 220, 50), Color.rgb(170, 80, 220))
    private val paint = fillP()
    private val stroke = lineP().apply { strokeWidth = 5f }
    private val path = Path()

    override fun onReset(now: Long) { items.clear(); bx.fill(-1f); by.fill(-1f); lastSpawn = 0L }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        val n = max(1, players.size)
        val el = now - phaseStart
        val gap = (520f / (1f + 0.35f * (n - 1))).toLong()
        if (now - lastSpawn > gap) {
            val roll = Random.nextFloat()
            val type = if (roll < 0.1f) 5 else if (roll < 0.28f) 6 else Random.nextInt(5)
            items.add(It(w * (0.06f + 0.88f * Random.nextFloat()), -h * 0.05f, h * (0.00045f + 0.0004f * Random.nextFloat()) * (1f + el / 120000f), type))
            lastSpawn = now
        }
        for (p in players) {
            if (p.n < 2) continue
            val tx = p.x(0, w); val ty = p.y(0, h) - h * 0.13f
            val k = min(1f, dt / 80f)
            bx[p.slot] = if (bx[p.slot] < 0f) tx else bx[p.slot] + (tx - bx[p.slot]) * k
            by[p.slot] = if (by[p.slot] < 0f) ty else by[p.slot] + (ty - by[p.slot]) * k
        }
        val r = min(w, h) * 0.04f
        val half = h * 0.09f
        val iter = items.iterator()
        loop@ while (iter.hasNext()) {
            val it = iter.next()
            it.y += it.vy * dt
            if (it.y > h + r) { iter.remove(); continue }
            for (p in players) {
                val s = p.slot
                if (bx[s] < 0f) continue
                if (abs(it.x - bx[s]) < half + r * 0.3f && it.y > by[s] - r - it.vy * dt && it.y < by[s] + r * 0.8f) {
                    when {
                        it.type == 6 -> { scores[s] = max(0, scores[s] - 3); Sfx.hit(); Fx.shake(250f, h * 0.01f); Fx.burst(it.x, it.y, Color.rgb(255, 120, 30), 22, 0.6f, h * 0.01f); Fx.text(it.x, it.y, "-3", Color.RED, true) }
                        it.type == 5 -> { scores[s] += 3; Sfx.pop(); Fx.burst(it.x, it.y, Color.rgb(255, 215, 60), 24, 0.5f, h * 0.009f); Fx.text(it.x, it.y, "+3", PLAYER_COLORS[s], true) }
                        else -> { scores[s] += 1; Sfx.pop(); Fx.burst(it.x, it.y, cols[it.type], 12, 0.4f, h * 0.008f); Fx.text(it.x, it.y, "+1", PLAYER_COLORS[s]) }
                    }
                    iter.remove(); continue@loop
                }
            }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = GameClock.now()
        val r = min(w, h) * 0.04f
        for (it in items) {
            when (it.type) {
                6 -> { paint.color = Color.rgb(35, 35, 45); c.drawCircle(it.x, it.y, r, paint)
                       stroke.color = withAlpha(Color.RED, 160 + (90 * sin(now / 120f)).toInt()); stroke.strokeWidth = r * 0.12f; c.drawCircle(it.x, it.y, r, stroke)
                       paint.color = if ((now / 120L) % 2L == 0L) Color.rgb(255, 170, 40) else Color.rgb(255, 240, 90); c.drawCircle(it.x + r * 0.5f, it.y - r * 1.15f, r * 0.2f, paint) }
                5 -> { paint.color = withAlpha(Color.rgb(255, 215, 60), 80); c.drawCircle(it.x, it.y, r * 1.5f, paint)
                       paint.color = Color.rgb(255, 205, 40); c.drawCircle(it.x, it.y, r, paint)
                       paint.color = withAlpha(Color.WHITE, 160); c.drawCircle(it.x - r * 0.3f, it.y - r * 0.3f, r * 0.25f, paint) }
                else -> { paint.color = cols[it.type]; c.drawCircle(it.x, it.y, r, paint)
                          paint.color = withAlpha(Color.WHITE, 110); c.drawCircle(it.x - r * 0.3f, it.y - r * 0.3f, r * 0.22f, paint)
                          paint.color = Color.rgb(60, 170, 70); c.drawOval(RectF(it.x, it.y - r * 1.25f, it.x + r * 0.5f, it.y - r * 0.85f), paint) }
            }
        }
        val half = h * 0.09f
        for (p in players) {
            val s = p.slot
            if (bx[s] < 0f) continue
            val x = bx[s]; val y = by[s]
            path.reset()
            path.moveTo(x - half, y - half * 0.35f); path.lineTo(x + half, y - half * 0.35f)
            path.lineTo(x + half * 0.7f, y + half * 0.45f); path.lineTo(x - half * 0.7f, y + half * 0.45f); path.close()
            paint.color = Color.rgb(160, 105, 55); c.drawPath(path, paint)
            stroke.color = withAlpha(Color.rgb(100, 60, 30), 200); stroke.strokeWidth = 3f
            for (k in -2..2) c.drawLine(x + k * half * 0.38f, y - half * 0.35f, x + k * half * 0.27f, y + half * 0.45f, stroke)
            c.drawLine(x - half * 0.85f, y + half * 0.05f, x + half * 0.85f, y + half * 0.05f, stroke)
            stroke.color = PLAYER_COLORS[s]; stroke.strokeWidth = 9f
            c.drawLine(x - half, y - half * 0.35f, x + half, y - half * 0.35f, stroke)
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx - s * 0.75f, cy + s * 0.15f); path.lineTo(cx + s * 0.75f, cy + s * 0.15f); path.lineTo(cx + s * 0.5f, cy + s * 0.75f); path.lineTo(cx - s * 0.5f, cy + s * 0.75f); path.close()
        paint.color = Color.rgb(160, 105, 55); c.drawPath(path, paint)
        paint.color = Color.rgb(230, 50, 60); c.drawCircle(cx - s * 0.2f, cy - s * 0.35f, s * 0.3f, paint)
        paint.color = Color.rgb(250, 220, 50); c.drawCircle(cx + s * 0.35f, cy - s * 0.1f, s * 0.25f, paint)
    }
}

// ======================= 10. Quiz Zones =======================
class QuizGame : Game("quiz", "Quiz Zones", "Stand in the zone with the right answer. 130+ questions!") {
    override val hint = "Stand in the right zone"
    override val tag = "1-4 players - 130+ questions"
    override val color = Color.rgb(60, 150, 230)
    override val bgTop = Color.rgb(8, 22, 56)
    override val bgBottom = Color.rgb(26, 72, 134)
    override val roundMs = 0L
    override val joinLate = false

    private class Q(val cat: String, val q: String, val a: Array<String>, val c: Int)
    private fun qz(cat: String, q: String, a: String, b: String, c: String, d: String, ans: Int) = Q(cat, q, arrayOf(a, b, c, d), ans)
    private val bank = listOf(
        qz("Maths", "9 x 7 = ?", "56", "63", "72", "81", 1),
        qz("Maths", "What is 144 / 12?", "10", "11", "12", "14", 2),
        qz("Maths", "What is 25% of 200?", "25", "40", "50", "75", 2),
        qz("Maths", "Which number is a prime number?", "21", "27", "29", "33", 2),
        qz("Maths", "How many degrees are in a right angle?", "45", "90", "120", "180", 1),
        qz("Maths", "15 x 15 = ?", "205", "215", "225", "235", 2),
        qz("Maths", "What is the square root of 81?", "7", "8", "9", "10", 2),
        qz("Maths", "How many sides does an octagon have?", "6", "7", "8", "9", 2),
        qz("Maths", "1000 - 357 = ?", "633", "643", "653", "743", 1),
        qz("Maths", "What comes next: 2, 4, 8, 16, ?", "20", "24", "30", "32", 3),
        qz("Maths", "How many minutes are in 3 hours?", "120", "150", "180", "200", 2),
        qz("Maths", "Which is the biggest number?", "0.9", "0.09", "0.99", "0.19", 2),
        qz("Maths", "8 x 12 = ?", "86", "92", "96", "108", 2),
        qz("Maths", "Half of 90 is?", "35", "40", "45", "55", 2),
        qz("Maths", "How many sides does a triangle have?", "2", "3", "4", "5", 1),
        qz("Maths", "7 x 8 = ?", "54", "56", "58", "64", 1),
        qz("Maths", "15 + 27 = ?", "41", "42", "43", "52", 1),
        qz("Maths", "100 / 4 = ?", "20", "25", "30", "40", 1),
        qz("Maths", "12 x 12 = ?", "124", "132", "144", "154", 2),
        qz("Maths", "What is the smallest prime number?", "0", "1", "2", "3", 2),
        qz("Maths", "How many sides does a hexagon have?", "5", "6", "7", "8", 1),
        qz("Maths", "How many minutes are in 2 hours?", "100", "110", "120", "150", 2),
        qz("Maths", "How many days are in a leap year?", "364", "365", "366", "367", 2),
        qz("Science", "Which planet is called the Red Planet?", "Venus", "Mars", "Jupiter", "Saturn", 1),
        qz("Science", "Which gas do plants take in?", "Oxygen", "Carbon dioxide", "Nitrogen", "Helium", 1),
        qz("Science", "H2O is the formula for?", "Salt", "Water", "Sugar", "Acid", 1),
        qz("Science", "Which planet is closest to the Sun?", "Venus", "Earth", "Mercury", "Mars", 2),
        qz("Science", "Blue mixed with yellow makes?", "Green", "Purple", "Orange", "Brown", 0),
        qz("Science", "Which organ pumps blood around the body?", "Brain", "Lungs", "Heart", "Liver", 2),
        qz("Science", "Which planet is the largest?", "Earth", "Jupiter", "Saturn", "Neptune", 1),
        qz("Science", "What do bees make?", "Milk", "Honey", "Silk", "Sugar", 1),
        qz("Science", "At sea level, water boils at?", "50 C", "80 C", "100 C", "150 C", 2),
        qz("Science", "Which part of a plant makes its food?", "Root", "Stem", "Leaf", "Seed", 2),
        qz("Science", "How many bones does an adult human have?", "106", "206", "306", "406", 1),
        qz("Science", "What force pulls things towards the ground?", "Magnetism", "Gravity", "Friction", "Wind", 1),
        qz("Science", "Which is the hardest natural substance?", "Gold", "Iron", "Diamond", "Quartz", 2),
        qz("Science", "What is Earth's natural satellite?", "Sun", "Mars", "Moon", "Venus", 2),
        qz("Science", "Which vitamin do we get from sunlight?", "A", "C", "D", "K", 2),
        qz("Science", "Which gas makes party balloons float?", "Oxygen", "Helium", "Carbon dioxide", "Nitrogen", 1),
        qz("Science", "Which blood cells carry oxygen?", "White cells", "Red cells", "Platelets", "Plasma", 1),
        qz("Science", "What is the centre of an atom called?", "Electron", "Nucleus", "Orbit", "Cell", 1),
        qz("Science", "Sunlight takes about how long to reach Earth?", "8 seconds", "8 minutes", "8 hours", "8 days", 1),
        qz("Science", "Which planet is famous for its rings?", "Mars", "Saturn", "Mercury", "Venus", 1),
        qz("Science", "What is the chemical symbol for gold?", "Go", "Gd", "Au", "Ag", 2),
        qz("Science", "Which organ helps us breathe?", "Heart", "Liver", "Lungs", "Kidney", 2),
        qz("Science", "What is frozen water called?", "Steam", "Ice", "Dew", "Fog", 1),
        qz("Science", "Which one is a renewable energy source?", "Coal", "Solar power", "Petrol", "Diesel", 1),
        qz("Science", "Which metal is liquid at room temperature?", "Iron", "Mercury", "Copper", "Tin", 1),
        qz("Geography", "What is the capital of India?", "Mumbai", "Kolkata", "New Delhi", "Chennai", 2),
        qz("Geography", "Which is the largest ocean?", "Atlantic", "Indian", "Arctic", "Pacific", 3),
        qz("Geography", "How many continents are there?", "5", "6", "7", "8", 2),
        qz("Geography", "In which direction does the sun rise?", "West", "North", "South", "East", 3),
        qz("Geography", "The Taj Mahal is in which city?", "Delhi", "Agra", "Jaipur", "Lucknow", 1),
        qz("Geography", "What is the capital of Japan?", "Seoul", "Beijing", "Tokyo", "Bangkok", 2),
        qz("Geography", "Which river is holy and flows through Varanasi?", "Yamuna", "Ganga", "Godavari", "Krishna", 1),
        qz("Geography", "What is the capital of France?", "Rome", "Madrid", "Paris", "Berlin", 2),
        qz("Geography", "Which is the largest country by area?", "Canada", "China", "USA", "Russia", 3),
        qz("Geography", "Mount Everest is part of which mountain range?", "Alps", "Andes", "Himalayas", "Rockies", 2),
        qz("Geography", "Which desert is found in Rajasthan?", "Sahara", "Thar", "Gobi", "Kalahari", 1),
        qz("Geography", "Which is the smallest continent?", "Europe", "Australia", "Antarctica", "South America", 1),
        qz("Geography", "Which ocean lies south of India?", "Pacific", "Atlantic", "Indian", "Arctic", 2),
        qz("Geography", "What is the capital of Australia?", "Sydney", "Melbourne", "Canberra", "Perth", 2),
        qz("Geography", "In which country is the Eiffel Tower?", "Italy", "France", "Spain", "UK", 1),
        qz("Geography", "Which state is the Land of Five Rivers?", "Haryana", "Punjab", "Gujarat", "Bihar", 1),
        qz("Geography", "Which Indian city is called the Pink City?", "Jodhpur", "Jaipur", "Udaipur", "Agra", 1),
        qz("Geography", "Which country has the pyramids of Giza?", "Egypt", "Iraq", "Peru", "Greece", 0),
        qz("Geography", "Which is the largest Indian state by area?", "Maharashtra", "Rajasthan", "Uttar Pradesh", "Madhya Pradesh", 1),
        qz("Geography", "The Great Wall is in which country?", "Japan", "India", "China", "Korea", 2),
        qz("Geography", "How many states does India have?", "26", "28", "29", "30", 1),
        qz("Geography", "What is the capital of Kerala?", "Kochi", "Thiruvananthapuram", "Kozhikode", "Thrissur", 1),
        qz("Geography", "The Gateway of India is in which city?", "Delhi", "Mumbai", "Chennai", "Kolkata", 1),
        qz("General", "What is India's national animal?", "Lion", "Elephant", "Tiger", "Peacock", 2),
        qz("General", "Which festival is the festival of lights?", "Holi", "Diwali", "Eid", "Pongal", 1),
        qz("General", "Who is called the Father of the Nation in India?", "Nehru", "Mahatma Gandhi", "Subhas Bose", "Sardar Patel", 1),
        qz("General", "The Indian flag has how many main colours?", "2", "3", "4", "5", 1),
        qz("General", "What is India's national bird?", "Sparrow", "Peacock", "Parrot", "Eagle", 1),
        qz("General", "What is India's national fruit?", "Apple", "Banana", "Mango", "Guava", 2),
        qz("General", "Which festival is celebrated with colours?", "Diwali", "Holi", "Onam", "Lohri", 1),
        qz("General", "Who wrote India's national anthem?", "Rabindranath Tagore", "Bankim Chandra", "Muhammad Iqbal", "Premchand", 0),
        qz("General", "What is India's currency?", "Dollar", "Rupee", "Yen", "Taka", 1),
        qz("General", "How many colours are in a rainbow?", "5", "6", "7", "8", 2),
        qz("General", "Which is India's national flower?", "Rose", "Lotus", "Marigold", "Jasmine", 1),
        qz("General", "How many months have 31 days?", "5", "6", "7", "8", 2),
        qz("General", "The Red Fort is in which city?", "Agra", "Delhi", "Jaipur", "Lahore", 1),
        qz("General", "Republic Day is celebrated on?", "15 August", "26 January", "2 October", "14 November", 1),
        qz("General", "Independence Day is celebrated on?", "26 January", "15 August", "2 October", "5 September", 1),
        qz("General", "Which animal is called the ship of the desert?", "Horse", "Camel", "Donkey", "Elephant", 1),
        qz("General", "Teachers' Day in India is on?", "14 November", "5 September", "2 October", "1 May", 1),
        qz("General", "Who invented the telephone?", "Edison", "Alexander Graham Bell", "Newton", "Tesla", 1),
        qz("Sports", "How many players are on a football team on the pitch?", "9", "10", "11", "12", 2),
        qz("Sports", "Which sport uses a shuttlecock?", "Tennis", "Badminton", "Squash", "Golf", 1),
        qz("Sports", "How many runs is a six in cricket?", "4", "5", "6", "8", 2),
        qz("Sports", "Which country hosts the Wimbledon tennis tournament?", "USA", "France", "England", "Australia", 2),
        qz("Sports", "How many rings are in the Olympic symbol?", "4", "5", "6", "7", 1),
        qz("Sports", "Sachin Tendulkar is famous for which sport?", "Hockey", "Cricket", "Tennis", "Football", 1),
        qz("Sports", "Neeraj Chopra won gold in which sport?", "Javelin throw", "Boxing", "Wrestling", "Archery", 0),
        qz("Sports", "How many players are on court in a basketball team?", "5", "6", "7", "11", 0),
        qz("Sports", "In chess, which piece moves in an L shape?", "Bishop", "Knight", "Rook", "Pawn", 1),
        qz("Sports", "How many stumps are there in cricket?", "2", "3", "4", "5", 1),
        qz("Sports", "The football World Cup is held every?", "2 years", "3 years", "4 years", "5 years", 2),
        qz("Sports", "In which sport do you slam dunk?", "Volleyball", "Basketball", "Tennis", "Golf", 1),
        qz("Sports", "How many players are on the mat for one kabaddi team?", "5", "7", "9", "11", 1),
        qz("Sports", "How many players are in a cricket team?", "9", "10", "11", "12", 2),
        qz("Animals", "Which is the fastest land animal?", "Cheetah", "Horse", "Lion", "Leopard", 0),
        qz("Animals", "Which is the largest mammal?", "Elephant", "Blue whale", "Giraffe", "Hippo", 1),
        qz("Animals", "Which animal is called the king of the jungle?", "Tiger", "Lion", "Elephant", "Bear", 1),
        qz("Animals", "A baby kangaroo is called a?", "Pup", "Joey", "Cub", "Kit", 1),
        qz("Animals", "Which of these birds cannot fly?", "Crow", "Ostrich", "Eagle", "Pigeon", 1),
        qz("Animals", "How many hearts does an octopus have?", "1", "2", "3", "4", 2),
        qz("Animals", "Which animal has the longest neck?", "Elephant", "Giraffe", "Camel", "Horse", 1),
        qz("Animals", "What do giant pandas mainly eat?", "Fish", "Bamboo", "Meat", "Fruit", 1),
        qz("Animals", "Which mammal can truly fly?", "Bat", "Flying squirrel", "Penguin", "Ostrich", 0),
        qz("Animals", "A group of lions is called a?", "Pack", "Pride", "Herd", "Flock", 1),
        qz("Animals", "Which animal can change its colour to hide?", "Chameleon", "Crocodile", "Tortoise", "Rabbit", 0),
        qz("Animals", "Which is the largest bird?", "Eagle", "Ostrich", "Penguin", "Albatross", 1),
        qz("Animals", "Which animal gives us wool?", "Cow", "Sheep", "Dog", "Horse", 1),
        qz("Animals", "How many legs does an insect have?", "4", "6", "8", "10", 1),
        qz("Animals", "How many legs does a spider have?", "6", "8", "10", "12", 1),
        qz("Animals", "Which sea animal has eight arms?", "Octopus", "Jellyfish", "Shark", "Dolphin", 0),
        qz("Tech", "What does WWW stand for?", "World Wide Web", "Wide World Web", "Web World Wide", "World Web Wide", 0),
        qz("Tech", "Which company makes the Android system?", "Apple", "Google", "Microsoft", "Sony", 1),
        qz("Tech", "What does CPU stand for?", "Central Processing Unit", "Computer Power Unit", "Central Program Unit", "Control Panel Unit", 0),
        qz("Tech", "Which of these is a search engine?", "Google", "Excel", "Paint", "Notepad", 0),
        qz("Tech", "How many bits are in a byte?", "4", "8", "16", "32", 1),
        qz("Tech", "Which key deletes the letter to the left of the cursor?", "Enter", "Backspace", "Shift", "Tab", 1),
        qz("Tech", "What does USB stand for?", "Universal Serial Bus", "United System Box", "Universal Server Base", "Unique Serial Block", 0),
        qz("Tech", "Which language builds the structure of web pages?", "HTML", "Python", "C", "Java", 0),
        qz("History", "Who was the first Prime Minister of India?", "Gandhi", "Jawaharlal Nehru", "Sardar Patel", "Lal Bahadur Shastri", 1),
        qz("History", "In which year did India become independent?", "1942", "1947", "1950", "1952", 1),
        qz("History", "Who is famous for discovering gravity?", "Einstein", "Newton", "Galileo", "Edison", 1),
        qz("History", "Who painted the Mona Lisa?", "Picasso", "Leonardo da Vinci", "Van Gogh", "Michelangelo", 1),
        qz("History", "Which emperor built the Taj Mahal?", "Akbar", "Shah Jahan", "Babur", "Aurangzeb", 1),
        qz("History", "Which ancient people built the pyramids of Giza?", "Romans", "Egyptians", "Greeks", "Mayans", 1),
        qz("History", "Who was the first man on the Moon?", "Yuri Gagarin", "Neil Armstrong", "Buzz Aldrin", "Rakesh Sharma", 1),
        qz("History", "Who was the first Indian in space?", "Kalpana Chawla", "Rakesh Sharma", "Sunita Williams", "Vikram Sarabhai", 1),
        qz("History", "Who is called the Missile Man of India?", "Homi Bhabha", "A. P. J. Abdul Kalam", "C. V. Raman", "Vikram Sarabhai", 1),
        qz("History", "Who chaired the drafting committee of India's Constitution?", "Nehru", "Dr. B. R. Ambedkar", "Gandhi", "Sardar Patel", 1)
    )
    private var picks = ArrayList<Q>()
    private var qi = 0
    private var qStart = 0L
    private var reveal = false
    private val zoneOf = IntArray(4) { -1 }
    private val zc = intArrayOf(Color.rgb(230, 80, 80), Color.rgb(70, 140, 235), Color.rgb(70, 200, 110), Color.rgb(240, 200, 60))
    private val paint = fillP()
    private val stroke = lineP().apply { strokeWidth = 6f }
    private val txt = textP().apply { setShadowLayer(6f, 2f, 2f, Color.BLACK) }

    override fun onStart(now: Long) {
        if (quizDeck.size < 10) { quizDeck.clear(); quizDeck.addAll((0 until bank.size).shuffled()) }
        picks = ArrayList<Q>(); repeat(10) { picks.add(bank[quizDeck.removeAt(0)]) }
        qi = 0; qStart = now; reveal = false; zoneOf.fill(-1)
    }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        if (picks.isEmpty()) return
        zoneOf.fill(-1)
        for (p in players) if (p.n > 12) zoneOf[p.slot] = ((((p.x(11, w) + p.x(12, w)) / 2f) / w) * 4f).toInt().coerceIn(0, 3)
        val q = picks[qi]
        if (!reveal && now - qStart >= 8000L) {
            reveal = true; qStart = now
            for (p in players) {
                if (!joined[p.slot]) continue
                if (zoneOf[p.slot] == q.c) { scores[p.slot]++; Sfx.pop(); Fx.text(p.x(0, w), p.y(0, h) - h * 0.12f, "+1", Color.rgb(90, 230, 120), true) }
                else { Sfx.hit(); Fx.text(p.x(0, w), p.y(0, h) - h * 0.12f, "X", Color.RED, true) }
            }
        } else if (reveal && now - qStart >= 3000L) {
            qi++
            if (qi >= picks.size) finish() else { reveal = false; qStart = now }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        if (picks.isEmpty() || qi >= picks.size) return
        val now = GameClock.now()
        val q = picks[qi]
        val card = RectF(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.27f)
        paint.color = withAlpha(Color.rgb(6, 12, 30), 190); c.drawRoundRect(card, 30f, 30f, paint)
        drawCenterText(c, txt, "Question ${qi + 1} / ${picks.size}  -  ${q.cat}", w * 0.5f, h * 0.075f, h * 0.03f, Color.rgb(170, 190, 225))
        txt.textSize = h * 0.055f; txt.color = Color.WHITE
        wrapCenter(c, txt, q.q, w / 2, card.top + h * 0.075f, card.width() - 60f)
        if (!reveal) {
            val frac = (1f - (now - qStart) / 8000f).coerceIn(0f, 1f)
            paint.color = withAlpha(Color.WHITE, 60); c.drawRoundRect(RectF(w * 0.1f, h * 0.285f, w * 0.9f, h * 0.3f), 12f, 12f, paint)
            paint.color = if (frac < 0.25f) Color.rgb(255, 90, 90) else Color.rgb(120, 220, 255)
            c.drawRoundRect(RectF(w * 0.1f, h * 0.285f, w * 0.1f + w * 0.8f * frac, h * 0.3f), 12f, 12f, paint)
        }
        val letters = arrayOf("A", "B", "C", "D")
        for (i in 0 until 4) {
            val z = RectF(w * i / 4f + 8f, h * 0.34f, w * (i + 1) / 4f - 8f, h * 0.86f)
            val correct = i == q.c
            val a = if (!reveal) 120 else if (correct) 230 else 40
            paint.shader = LinearGradient(z.left, z.top, z.left, z.bottom, withAlpha(zc[i], a), withAlpha(darken(zc[i], 0.5f), a), Shader.TileMode.CLAMP)
            c.drawRoundRect(z, 28f, 28f, paint); paint.shader = null
            if (reveal && correct) { stroke.color = Color.WHITE; stroke.strokeWidth = 8f; c.drawRoundRect(z, 28f, 28f, stroke) }
            drawCenterText(c, txt, letters[i], z.centerX(), z.top + h * 0.12f, h * 0.12f, withAlpha(Color.WHITE, if (reveal && !correct) 90 else 255))
            txt.textSize = h * 0.042f; txt.color = withAlpha(Color.WHITE, if (reveal && !correct) 90 else 255)
            wrapCenter(c, txt, q.a[i], z.centerX(), z.top + h * 0.2f, z.width() - 30f)
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        for (i in 0 until 4) {
            paint.color = withAlpha(zc[i], 200)
            c.drawRoundRect(RectF(cx - s * 0.95f + i * s * 0.48f, cy - s * 0.1f, cx - s * 0.55f + i * s * 0.48f, cy + s * 0.85f), s * 0.1f, s * 0.1f, paint)
        }
        drawCenterText(c, txt, "?", cx, cy - s * 0.1f, s * 1.1f, Color.WHITE)
    }
}

// ======================= 11. Pose Match =======================
class PoseGame : Game("pose", "Pose Match", "Copy the pose shown on screen before the time runs out!") {
    override val hint = "Copy the pose!"
    override val color = Color.rgb(240, 160, 40)
    override val bgTop = Color.rgb(48, 26, 6)
    override val bgBottom = Color.rgb(132, 72, 20)
    override val roundMs = 0L
    override val joinLate = false

    private val names = arrayOf("Hands up!", "T-pose!", "Left hand up!", "Right hand up!", "Hands on your head!", "Namaste!", "Cross your arms!")
    private val rounds = 10
    private var ri = 0
    private var target = 0
    private var rStart = 0L
    private var inGap = false
    private val holdStart = LongArray(4)
    private val done = BooleanArray(4)
    private var firstDone = false
    private val paint = fillP()
    private val line = lineP()
    private val txt = textP().apply { setShadowLayer(6f, 2f, 2f, Color.BLACK) }

    private fun matches(p: Player, id: Int): Boolean {
        if (p.n < 25) return false
        val nose = p.rawY(0)
        val shY = (p.rawY(11) + p.rawY(12)) / 2f
        val hipY = (p.rawY(23) + p.rawY(24)) / 2f
        val torso = hipY - shY
        if (torso < 0.04f) return false
        val sw = abs(p.rawX(11) - p.rawX(12))
        val cx = (p.rawX(11) + p.rawX(12)) / 2f
        val lw = p.rawY(15); val rw = p.rawY(16)
        val lUp = lw < nose; val rUp = rw < nose
        val lDown = lw > shY + 0.25f * torso; val rDown = rw > shY + 0.25f * torso
        return when (id) {
            0 -> lUp && rUp
            1 -> abs(lw - shY) < 0.4f * torso && abs(rw - shY) < 0.4f * torso && abs(p.rawX(15) - cx) > 1.4f * sw && abs(p.rawX(16) - cx) > 1.4f * sw
            2 -> lUp && rDown
            3 -> rUp && lDown
            4 -> hypot(p.rawX(15) - p.rawX(0), (lw - nose) * 0.75f) < sw * 0.9f && hypot(p.rawX(16) - p.rawX(0), (rw - nose) * 0.75f) < sw * 0.9f
            5 -> abs(p.rawX(15) - p.rawX(16)) < 0.45f * sw && lw > shY && lw < hipY && rw > shY && rw < hipY
            else -> p.rawX(15) < p.rawX(16) && lw > shY && lw < shY + torso && rw > shY && rw < shY + torso &&
                    abs(p.rawX(15) - cx) < 1.2f * sw && abs(p.rawX(16) - cx) < 1.2f * sw
        }
    }

    private fun nextRound(now: Long) {
        var t = Random.nextInt(names.size)
        if (t == target) t = (t + 1 + Random.nextInt(names.size - 1)) % names.size
        target = t; rStart = now; inGap = false; done.fill(false); holdStart.fill(0L); firstDone = false
    }

    override fun onStart(now: Long) { ri = 0; target = -1; nextRound(now) }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        if (!inGap) {
            for (p in players) {
                val s = p.slot
                if (!joined[s] || done[s]) continue
                if (matches(p, target)) {
                    if (holdStart[s] == 0L) holdStart[s] = now
                    if (now - holdStart[s] >= 400L) {
                        done[s] = true
                        val pts = if (!firstDone) 2 else 1
                        firstDone = true; scores[s] += pts; Sfx.pop()
                        Fx.text(p.x(0, w), p.y(0, h) - h * 0.12f, "+$pts", PLAYER_COLORS[s], true)
                        Fx.burst(p.x(0, w), p.y(0, h), PLAYER_COLORS[s], 16, 0.45f, h * 0.009f)
                    }
                } else holdStart[s] = 0L
            }
            val all = (0 until 4).filter { joined[it] }
            if (now - rStart >= 5000L || (all.isNotEmpty() && all.all { done[it] })) { inGap = true; rStart = now }
        } else if (now - rStart >= 1200L) {
            ri++
            if (ri >= rounds) finish() else nextRound(now)
        }
    }

    private fun guide(c: Canvas, id: Int, cx: Float, cy: Float, s: Float) {
        val lx = floatArrayOf(-0.6f, -1.0f, -0.55f, -0.3f, -0.12f, -0.05f, 0.28f)
        val ly = floatArrayOf(-1.25f, -0.62f, -1.25f, -0.1f, -1.0f, -0.35f, -0.35f)
        val rx = floatArrayOf(0.6f, 1.0f, 0.3f, 0.55f, 0.12f, 0.05f, -0.28f)
        val ry = floatArrayOf(-1.25f, -0.62f, -0.1f, -1.25f, -1.0f, -0.35f, -0.35f)
        line.strokeWidth = s * 0.1f; line.color = Color.WHITE
        paint.color = Color.WHITE; c.drawCircle(cx, cy - s * 0.88f, s * 0.17f, paint)
        c.drawLine(cx, cy - s * 0.62f, cx, cy + s * 0.1f, line)
        c.drawLine(cx, cy + s * 0.1f, cx - s * 0.3f, cy + s * 0.85f, line); c.drawLine(cx, cy + s * 0.1f, cx + s * 0.3f, cy + s * 0.85f, line)
        for (side in 0 until 2) {
            val sgn = if (side == 0) -1f else 1f
            val shx = cx + sgn * s * 0.25f; val shy = cy - s * 0.6f
            val wx = cx + (if (side == 0) lx[id] else rx[id]) * s; val wy = cy + (if (side == 0) ly[id] else ry[id]) * s
            val ex: Float; val ey: Float
            if (id == 4) { ex = cx + sgn * s * 0.55f; ey = cy - s * 0.75f }
            else if (id == 5) { ex = cx + sgn * s * 0.4f; ey = cy - s * 0.2f }
            else { ex = (shx + wx) / 2f; ey = (shy + wy) / 2f }
            c.drawLine(shx, shy, ex, ey, line); c.drawLine(ex, ey, wx, wy, line)
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        if (target < 0) return
        val now = GameClock.now()
        val card = RectF(w * 0.36f, h * 0.12f, w * 0.64f, h * 0.55f)
        paint.color = withAlpha(Color.rgb(6, 8, 20), 170); c.drawRoundRect(card, 30f, 30f, paint)
        guide(c, target, card.centerX(), card.top + card.height() * 0.62f, card.height() * 0.3f)
        drawCenterText(c, txt, names[target], w / 2, card.bottom + h * 0.07f, h * 0.07f, Color.WHITE)
        drawCenterText(c, txt, "Round ${min(ri + 1, rounds)} / $rounds", w * 0.5f, h * 0.085f, h * 0.03f, Color.rgb(230, 210, 170))
        if (!inGap) {
            val frac = (1f - (now - rStart) / 5000f).coerceIn(0f, 1f)
            paint.color = withAlpha(Color.WHITE, 60); c.drawRoundRect(RectF(w * 0.3f, card.bottom + h * 0.1f, w * 0.7f, card.bottom + h * 0.12f), 10f, 10f, paint)
            paint.color = if (frac < 0.25f) Color.rgb(255, 90, 90) else Color.rgb(255, 210, 90)
            c.drawRoundRect(RectF(w * 0.3f, card.bottom + h * 0.1f, w * 0.3f + w * 0.4f * frac, card.bottom + h * 0.12f), 10f, 10f, paint)
        }
        for (p in players) if (joined[p.slot] && done[p.slot]) {
            line.color = Color.rgb(90, 230, 120); line.strokeWidth = 10f
            c.drawCircle(p.x(0, w), p.y(0, h), h * 0.075f, line)
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        line.strokeWidth = s * 0.14f; line.color = Color.WHITE
        paint.color = Color.WHITE; c.drawCircle(cx, cy - s * 0.72f, s * 0.2f, paint)
        c.drawLine(cx, cy - s * 0.5f, cx, cy + s * 0.15f, line)
        c.drawLine(cx - s * 0.8f, cy - s * 0.35f, cx + s * 0.8f, cy - s * 0.35f, line)
        c.drawLine(cx, cy + s * 0.15f, cx - s * 0.4f, cy + s * 0.8f, line); c.drawLine(cx, cy + s * 0.15f, cx + s * 0.4f, cy + s * 0.8f, line)
    }
}

// ======================= Balloon Keep-Up (team game) =======================
class BalloonGame : Game("balloon", "Balloon Keep-Up", "Keep the balloons in the air with your hands and head. Work as a team!") {
    override val hint = "Keep the balloon in the air!"
    override val tag = "1-4 players - teamwork"
    override val color = Color.rgb(250, 90, 140)
    override val bgTop = Color.rgb(36, 96, 170)
    override val bgBottom = Color.rgb(150, 210, 240)
    override val roundMs = 0L
    override fun status(slot: Int): String = if (joined[slot]) "${scores[slot]} hits" else ""
    override fun bestValue(): Int = total
    override fun resultHeadline(ranked: List<Int>): String? = "Team: $total hits!"

    private class Bl(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, var hitAt: Long)
    private val balloons = ArrayList<Bl>()
    private var lives = 3
    private var total = 0
    private val hitIdx = intArrayOf(0, 15, 16, 19, 20, 13, 14)
    private val cols = intArrayOf(Color.rgb(240, 70, 90), Color.rgb(255, 205, 50), Color.rgb(160, 90, 230))
    private val paint = fillP()
    private val line = lineP().apply { strokeWidth = 3f }
    private val txt = textP()
    private val path = Path()

    override fun onReset(now: Long) { balloons.clear(); lives = 3; total = 0 }

    private fun spawn(w: Float, h: Float, now: Long) {
        balloons.add(Bl(w * (0.25f + 0.5f * Random.nextFloat()), h * 0.2f, (Random.nextFloat() - 0.5f) * 0.1f, 0f, cols[balloons.size % 3], now))
    }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        if (balloons.isEmpty()) spawn(w, h, now)
        if (total >= 15 && balloons.size < 2) spawn(w, h, now)
        if (total >= 40 && balloons.size < 3) spawn(w, h, now)
        val r = min(w, h) * 0.07f
        val g = h * 0.0000011f * (1f + total / 90f)
        val d = dt.toFloat()
        val ground = h * 0.9f
        for (b in balloons) {
            b.vy = min(b.vy + g * d, h * 0.0011f)
            b.vx *= max(0f, 1f - 0.0006f * d)
            b.x += b.vx * d; b.y += b.vy * d
            if (b.x < r) { b.x = r; b.vx = abs(b.vx) * 0.8f } else if (b.x > w - r) { b.x = w - r; b.vx = -abs(b.vx) * 0.8f }
            if (b.y < r) { b.y = r; b.vy = abs(b.vy) * 0.5f }

            outer@ for (p in players) for (i in hitIdx) {
                if (i >= p.n) continue
                val px = p.x(i, w); val py = p.y(i, h)
                val dx = b.x - px; val dy = b.y - py
                val dist = hypot(dx, dy)
                if (dist < r * 1.15f + 14f && now - b.hitAt > 130L) {
                    var nx = dx / max(1f, dist); var ny = dy / max(1f, dist) - 0.7f
                    val len = max(0.001f, hypot(nx, ny)); nx /= len; ny /= len
                    val sp = h * 0.00085f
                    b.vx = nx * sp * 0.9f + b.vx * 0.15f; b.vy = ny * sp
                    b.hitAt = now; scores[p.slot]++; total++; Sfx.pop()
                    Fx.burst(px, py, PLAYER_COLORS[p.slot], 8, 0.35f, h * 0.007f)
                    if (total % 10 == 0) Fx.text(b.x, b.y - r * 1.5f, "$total!", Color.rgb(255, 235, 120), true)
                    break@outer
                }
            }

            if (b.y + r * 1.1f > ground) {
                lives--; Sfx.hit(); Fx.shake(300f, h * 0.012f)
                Fx.burst(b.x, ground, b.color, 24, 0.55f, h * 0.01f)
                Fx.text(b.x, ground - r, "Oops!", Color.rgb(255, 90, 90), true)
                b.x = w * (0.3f + 0.4f * Random.nextFloat()); b.y = h * 0.15f; b.vx = 0f; b.vy = 0f; b.hitAt = now
                if (lives <= 0) { finish(); return }
            }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = GameClock.now()
        val r = min(w, h) * 0.07f
        val ground = h * 0.9f
        // clouds
        paint.color = withAlpha(Color.WHITE, 90)
        for (k in 0 until 3) {
            val cx = ((k * 0.37f + now * 0.000004f * (1 + k)) % 1.3f - 0.15f) * w
            val cy = h * (0.18f + 0.17f * k)
            c.drawOval(RectF(cx - w * 0.07f, cy - h * 0.03f, cx + w * 0.07f, cy + h * 0.03f), paint)
            c.drawOval(RectF(cx - w * 0.04f, cy - h * 0.06f, cx + w * 0.04f, cy + h * 0.01f), paint)
        }
        // ground
        paint.color = withAlpha(Color.rgb(40, 140, 70), 190); c.drawRect(0f, ground, w, h, paint)
        paint.color = withAlpha(Color.rgb(120, 220, 140), 200); c.drawRect(0f, ground, w, ground + 6f, paint)
        // team counter
        drawCenterText(c, txt, "$total", w / 2, h * 0.26f, h * 0.2f, withAlpha(Color.WHITE, 70))
        drawCenterText(c, txt, "TEAM HITS", w / 2, h * 0.07f, h * 0.03f, withAlpha(Color.WHITE, 200))
        paint.color = Color.rgb(255, 70, 90)
        for (i in 0 until 3) {
            paint.color = if (i < lives) Color.rgb(255, 70, 90) else withAlpha(Color.WHITE, 90)
            drawHeart(c, w * 0.05f + i * h * 0.06f, h * 0.07f, h * 0.022f, paint)
        }
        for (b in balloons) {
            val sq = max(0f, 1f - (now - b.hitAt) / 160f)
            val rx = r * 0.9f * (1f + 0.18f * sq); val ry = r * 1.1f * (1f - 0.12f * sq)
            // shadow on the ground
            val hh = ((ground - b.y) / h).coerceIn(0f, 1f)
            paint.color = withAlpha(Color.BLACK, (60 * (1f - hh)).toInt()); c.drawOval(RectF(b.x - r * 0.8f, ground - 6f, b.x + r * 0.8f, ground + 10f), paint)
            // string
            path.reset(); path.moveTo(b.x, b.y + ry); path.quadTo(b.x - r * 0.3f, b.y + ry + r * 0.6f, b.x, b.y + ry + r * 1.2f); path.quadTo(b.x + r * 0.3f, b.y + ry + r * 1.8f, b.x - r * 0.05f, b.y + ry + r * 2.4f)
            line.color = withAlpha(Color.WHITE, 200); c.drawPath(path, line)
            // knot + body
            paint.color = darken(b.color, 0.25f)
            path.reset(); path.moveTo(b.x, b.y + ry - 2f); path.lineTo(b.x - r * 0.12f, b.y + ry + r * 0.14f); path.lineTo(b.x + r * 0.12f, b.y + ry + r * 0.14f); path.close(); c.drawPath(path, paint)
            paint.color = withAlpha(b.color, 255); c.drawOval(RectF(b.x - rx, b.y - ry, b.x + rx, b.y + ry), paint)
            paint.color = withAlpha(lighten(b.color, 0.5f), 120); c.drawOval(RectF(b.x - rx * 0.75f, b.y - ry * 0.8f, b.x + rx * 0.2f, b.y + ry * 0.1f), paint)
            paint.color = withAlpha(Color.WHITE, 220); c.drawOval(RectF(b.x - rx * 0.55f, b.y - ry * 0.65f, b.x - rx * 0.25f, b.y - ry * 0.3f), paint)
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        line.color = withAlpha(Color.WHITE, 220); line.strokeWidth = s * 0.05f
        path.reset(); path.moveTo(cx, cy + s * 0.55f); path.quadTo(cx - s * 0.2f, cy + s * 0.75f, cx, cy + s * 0.9f); path.quadTo(cx + s * 0.2f, cy + s * 1.05f, cx - s * 0.03f, cy + s * 1.2f); c.drawPath(path, line)
        paint.color = Color.rgb(240, 70, 90); c.drawOval(RectF(cx - s * 0.5f, cy - s * 0.75f, cx + s * 0.5f, cy + s * 0.58f), paint)
        paint.color = withAlpha(Color.WHITE, 200); c.drawOval(RectF(cx - s * 0.3f, cy - s * 0.55f, cx - s * 0.12f, cy - s * 0.25f), paint)
    }
}
