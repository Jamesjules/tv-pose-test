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

// ======================= 6. Sliding Puzzle =======================
class PuzzleGame : Game("puzzle", "Sliding Puzzle", "Slide the tiles to rebuild the picture. Race up to 4 players!") {
    override val hint = "Raise a hand - hover a tile to slide it"
    override val color = Color.rgb(120, 90, 220)
    override val bgTop = Color.rgb(20, 16, 50)
    override val bgBottom = Color.rgb(70, 40, 120)
    override val roundMs = 300000L
    override val joinLate = false
    override fun fmtBest(score: Int): String = "${600 - score}s"

    private val boards = Array(4) { IntArray(9) }
    private val moves = IntArray(4)
    private val solvedAt = LongArray(4)
    private val hoverCell = IntArray(4) { -1 }
    private val hoverStart = LongArray(4)
    private val cur = arrayOfNulls<PointF>(4)
    private val rects = Array(4) { RectF() }
    private val slots = ArrayList<Int>()
    private var pic: Bitmap? = null
    private var startAt = 0L
    private val paint = fillP()
    private val stroke = lineP().apply { strokeWidth = 3f }
    private val txt = textP()
    private val ip = fillP()

    private fun isSolved(b: IntArray): Boolean { for (i in 0 until 8) if (b[i] != i + 1) return false; return b[8] == 0 }

    private fun makeShuffle(): IntArray {
        while (true) {
            val b = IntArray(9) { if (it < 8) it + 1 else 0 }
            var e = 8; var lastE = -1
            repeat(90) {
                val opts = ArrayList<Int>()
                val r = e / 3; val cc = e % 3
                if (r > 0) opts.add(e - 3)
                if (r < 2) opts.add(e + 3)
                if (cc > 0) opts.add(e - 1)
                if (cc < 2) opts.add(e + 1)
                opts.remove(lastE)
                val t = opts[Random.nextInt(opts.size)]
                b[e] = b[t]; b[t] = 0; lastE = e; e = t
            }
            if (!isSolved(b)) return b
        }
    }

    private fun makePic(): Bitmap {
        val s = 360
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG); val path = Path()
        val f = s.toFloat()
        p.shader = LinearGradient(0f, 0f, 0f, f * 0.75f, Color.rgb(30, 20, 95), Color.rgb(255, 140, 80), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, f, f, p); p.shader = null
        p.color = withAlpha(Color.WHITE, 200)
        for (i in 0 until 16) cv.drawCircle(((i * 97) % s).toFloat(), ((i * 53) % (s / 3)).toFloat(), 2f + i % 3, p)
        p.color = Color.rgb(240, 240, 255); cv.drawCircle(f * 0.18f, f * 0.16f, f * 0.07f, p)
        p.color = Color.rgb(36, 22, 98); cv.drawCircle(f * 0.2f, f * 0.145f, f * 0.06f, p)
        p.color = withAlpha(Color.rgb(255, 235, 160), 70); cv.drawCircle(f * 0.68f, f * 0.5f, f * 0.22f, p)
        p.color = Color.rgb(255, 235, 160); cv.drawCircle(f * 0.68f, f * 0.5f, f * 0.14f, p)
        p.color = Color.rgb(75, 42, 115)
        path.moveTo(0f, f * 0.75f); path.lineTo(f * 0.2f, f * 0.48f); path.lineTo(f * 0.4f, f * 0.72f); path.lineTo(f * 0.6f, f * 0.54f)
        path.lineTo(f * 0.85f, f * 0.75f); path.lineTo(f, f * 0.6f); path.lineTo(f, f); path.lineTo(0f, f); path.close(); cv.drawPath(path, p)
        path.reset(); p.color = Color.rgb(30, 18, 60)
        path.moveTo(0f, f * 0.86f); path.lineTo(f * 0.3f, f * 0.7f); path.lineTo(f * 0.55f, f * 0.86f); path.lineTo(f * 0.8f, f * 0.72f)
        path.lineTo(f, f * 0.87f); path.lineTo(f, f); path.lineTo(0f, f); path.close(); cv.drawPath(path, p)
        p.color = Color.rgb(90, 55, 40); cv.drawRect(f * 0.13f, f * 0.8f, f * 0.28f, f * 0.92f, p)
        p.color = Color.rgb(210, 70, 60)
        path.reset(); path.moveTo(f * 0.11f, f * 0.8f); path.lineTo(f * 0.205f, f * 0.7f); path.lineTo(f * 0.3f, f * 0.8f); path.close(); cv.drawPath(path, p)
        p.color = Color.rgb(255, 220, 120); cv.drawRect(f * 0.18f, f * 0.84f, f * 0.23f, f * 0.9f, p)
        p.color = withAlpha(Color.WHITE, 190); p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.DEFAULT_BOLD; p.textSize = f * 0.12f
        cv.drawText("RAZ", f * 0.62f, f * 0.96f, p)
        return bmp
    }

    private fun layout(w: Float, h: Float) {
        val n = max(1, slots.size)
        val gap = w * 0.02f
        val size = min((w * 0.92f - gap * (n - 1)) / n, h * 0.58f)
        val total = n * size + gap * (n - 1)
        val x0 = (w - total) / 2f; val y0 = h * 0.24f
        for ((i, s) in slots.withIndex()) rects[s].set(x0 + i * (size + gap), y0, x0 + i * (size + gap) + size, y0 + size)
    }

    override fun onStart(now: Long) {
        slots.clear(); for (i in 0 until 4) if (joined[i]) slots.add(i)
        val sh = makeShuffle()
        for (s in slots) { boards[s] = sh.copyOf(); moves[s] = 0; solvedAt[s] = 0L; hoverCell[s] = -1; cur[s] = null }
        startAt = now
        if (pic == null) pic = makePic()
    }

    private fun slide(b: IntArray, c: Int) {
        val e = b.indexOf(0)
        val er = e / 3; val ec = e % 3; val cr = c / 3; val cc = c % 3
        if (er == cr) {
            if (cc > ec) for (k in ec until cc) b[er * 3 + k] = b[er * 3 + k + 1]
            else for (k in ec downTo cc + 1) b[er * 3 + k] = b[er * 3 + k - 1]
        } else if (ec == cc) {
            if (cr > er) for (k in er until cr) b[k * 3 + ec] = b[(k + 1) * 3 + ec]
            else for (k in er downTo cr + 1) b[k * 3 + ec] = b[(k - 1) * 3 + ec]
        }
        b[c] = 0
    }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        if (slots.isEmpty()) return
        layout(w, h)
        for (s in slots) {
            if (solvedAt[s] > 0L) continue
            val r = rects[s]
            val p = players.firstOrNull { it.slot == s }
            cur[s] = smoothCursor(cur[s], if (p != null) cursorOf(p, r) else null)
            val cu = cur[s]
            if (cu == null) { hoverCell[s] = -1; continue }
            val cs = r.width() / 3f
            val col = ((cu.x - r.left) / cs).toInt().coerceIn(0, 2)
            val row = ((cu.y - r.top) / cs).toInt().coerceIn(0, 2)
            val cell = row * 3 + col
            if (cell != hoverCell[s]) { hoverCell[s] = cell; hoverStart[s] = now }
            val b = boards[s]
            val e = b.indexOf(0)
            val movable = cell != e && (cell / 3 == e / 3 || cell % 3 == e % 3)
            if (movable && now - hoverStart[s] >= 500L) {
                slide(b, cell); moves[s]++; Sfx.pop(); hoverStart[s] = now
                Fx.burst(r.left + (col + 0.5f) * cs, r.top + (row + 0.5f) * cs, PLAYER_COLORS[s], 10, 0.35f, h * 0.007f)
                if (isSolved(b)) {
                    solvedAt[s] = now; scores[s] = max(1, 600 - ((now - startAt) / 1000L).toInt())
                    Fx.burst(r.centerX(), r.centerY(), PLAYER_COLORS[s], 40, 0.7f, h * 0.012f)
                    Fx.text(r.centerX(), r.top, "SOLVED!", PLAYER_COLORS[s], true)
                    Sfx.over()
                }
            }
        }
        if (slots.all { solvedAt[it] > 0L }) finish()
    }

    override fun status(slot: Int): String =
        if (!joined[slot]) "" else if (solvedAt[slot] > 0L) "DONE ${600 - scores[slot]}s" else "${moves[slot]} moves"

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val pc = pic ?: return
        layout(w, h)
        val now = GameClock.now()
        val ps = pc.width / 3
        for (s in slots) {
            val r = rects[s]; val cs = r.width() / 3f
            paint.color = withAlpha(PLAYER_COLORS[s], 60); c.drawRoundRect(RectF(r.left - 14f, r.top - 14f, r.right + 14f, r.bottom + 14f), 26f, 26f, paint)
            paint.color = Color.argb(160, 6, 8, 20); c.drawRoundRect(r, 18f, 18f, paint)
            val b = boards[s]
            for (idx in 0 until 9) {
                val v = b[idx]; if (v == 0) continue
                val col = idx % 3; val row = idx / 3
                val tr = RectF(r.left + col * cs + 3f, r.top + row * cs + 3f, r.left + (col + 1) * cs - 3f, r.top + (row + 1) * cs - 3f)
                val hm = v - 1
                c.drawBitmap(pc, Rect((hm % 3) * ps, (hm / 3) * ps, (hm % 3) * ps + ps, (hm / 3) * ps + ps), tr, null)
                stroke.color = withAlpha(Color.WHITE, 140); stroke.strokeWidth = 3f; c.drawRect(tr, stroke)
                if (solvedAt[s] == 0L) {
                    paint.color = withAlpha(Color.BLACK, 140); c.drawCircle(tr.left + cs * 0.17f, tr.top + cs * 0.17f, cs * 0.12f, paint)
                    drawCenterText(c, txt, v.toString(), tr.left + cs * 0.17f, tr.top + cs * 0.17f + cs * 0.055f, cs * 0.16f, Color.WHITE)
                }
                if (hoverCell[s] == idx && solvedAt[s] == 0L) {
                    paint.color = withAlpha(Color.WHITE, 60); c.drawRect(tr, paint)
                    val pr = ((now - hoverStart[s]) / 500f).coerceIn(0f, 1f)
                    paint.color = PLAYER_COLORS[s]; c.drawRect(tr.left, tr.bottom - 10f, tr.left + tr.width() * pr, tr.bottom, paint)
                }
            }
            val head = if (solvedAt[s] > 0L) "${PLAYER_NAMES[s]}  DONE ${600 - scores[s]}s" else "${PLAYER_NAMES[s]}  ${moves[s]} moves"
            drawCenterText(c, txt, head, r.centerX(), r.top - h * 0.03f, h * 0.04f, PLAYER_COLORS[s])
            cur[s]?.let { if (solvedAt[s] == 0L) drawCursorDot(c, paint, it, PLAYER_COLORS[s], h) }
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        val cols = intArrayOf(Color.rgb(255, 120, 90), Color.rgb(255, 200, 70), Color.rgb(90, 200, 120), Color.rgb(90, 160, 255), Color.rgb(180, 110, 240), Color.rgb(255, 130, 190), Color.rgb(70, 210, 210), Color.rgb(250, 160, 60))
        val t = s * 0.62f; var k = 0
        for (i in 0 until 9) {
            if (i == 8) continue
            val x = cx + ((i % 3) - 1.5f) * t * 1.05f; val y = cy + ((i / 3) - 1.5f) * t * 1.05f
            ip.color = cols[k++]; c.drawRoundRect(RectF(x, y, x + t, y + t), t * 0.2f, t * 0.2f, ip)
        }
    }
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
class QuizGame : Game("quiz", "Quiz Zones", "Stand in the zone with the right answer. Think fast!") {
    override val hint = "Stand in the right zone"
    override val color = Color.rgb(60, 150, 230)
    override val bgTop = Color.rgb(8, 22, 56)
    override val bgBottom = Color.rgb(26, 72, 134)
    override val roundMs = 0L
    override val joinLate = false

    private class Q(val q: String, val a: Array<String>, val c: Int)
    private val bank = listOf(
        Q("7 x 8 = ?", arrayOf("54", "56", "58", "64"), 1),
        Q("Which planet is called the Red Planet?", arrayOf("Venus", "Mars", "Jupiter", "Saturn"), 1),
        Q("How many days are in a leap year?", arrayOf("364", "365", "366", "367"), 2),
        Q("What is the capital of India?", arrayOf("Mumbai", "Kolkata", "New Delhi", "Chennai"), 2),
        Q("Which gas do plants take in?", arrayOf("Oxygen", "Carbon dioxide", "Nitrogen", "Helium"), 1),
        Q("How many sides does a hexagon have?", arrayOf("5", "6", "7", "8"), 1),
        Q("Which is the largest ocean?", arrayOf("Atlantic", "Indian", "Arctic", "Pacific"), 3),
        Q("What is India's national animal?", arrayOf("Lion", "Elephant", "Tiger", "Peacock"), 2),
        Q("15 + 27 = ?", arrayOf("41", "42", "43", "52"), 1),
        Q("Which is the fastest land animal?", arrayOf("Cheetah", "Horse", "Lion", "Leopard"), 0),
        Q("How many continents are there?", arrayOf("5", "6", "7", "8"), 2),
        Q("H2O is the formula for?", arrayOf("Salt", "Water", "Sugar", "Acid"), 1),
        Q("Which planet is closest to the Sun?", arrayOf("Venus", "Earth", "Mercury", "Mars"), 2),
        Q("100 / 4 = ?", arrayOf("20", "25", "30", "40"), 1),
        Q("Which festival is the festival of lights?", arrayOf("Holi", "Diwali", "Eid", "Pongal"), 1),
        Q("How many players are in a cricket team?", arrayOf("9", "10", "11", "12"), 2),
        Q("Which organ pumps blood?", arrayOf("Brain", "Lungs", "Heart", "Liver"), 2),
        Q("Blue mixed with yellow makes?", arrayOf("Green", "Purple", "Orange", "Brown"), 0),
        Q("The Taj Mahal is in which city?", arrayOf("Delhi", "Agra", "Jaipur", "Lucknow"), 1),
        Q("12 x 12 = ?", arrayOf("124", "132", "144", "154"), 2),
        Q("Which is the largest mammal?", arrayOf("Elephant", "Blue whale", "Giraffe", "Hippo"), 1),
        Q("How many minutes are in 2 hours?", arrayOf("100", "110", "120", "150"), 2),
        Q("In which direction does the sun rise?", arrayOf("West", "North", "South", "East"), 3),
        Q("What is the smallest prime number?", arrayOf("0", "1", "2", "3"), 2)
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
        picks = ArrayList(bank.shuffled().take(8)); qi = 0; qStart = now; reveal = false; zoneOf.fill(-1)
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
        drawCenterText(c, txt, "Question ${qi + 1} / ${picks.size}", w * 0.5f, h * 0.075f, h * 0.03f, Color.rgb(170, 190, 225))
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
