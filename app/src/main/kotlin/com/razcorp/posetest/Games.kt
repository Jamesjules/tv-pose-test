package com.razcorp.posetest

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val HAND_IDX = intArrayOf(15, 16, 19, 20)

private fun iconFill() = Paint(Paint.ANTI_ALIAS_FLAG)
private fun iconLine() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.WHITE }

// ======================= 1. Bubble Pop =======================
class BubbleGame : Game("bubble", "Bubble Pop", "Pop bubbles with your hands. Gold ones are worth 5!") {
    override val hint = "Pop the bubbles"
    override val color = Color.rgb(0, 170, 230)
    override val bgTop = Color.rgb(5, 28, 66)
    override val bgBottom = Color.rgb(0, 112, 150)
    private class B(val x: Float, val y: Float, val born: Long, val gold: Boolean)
    private val bubbles = ArrayList<B>()
    private var lastSpawn = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val ip = iconFill()

    override fun onReset(now: Long) { bubbles.clear(); lastSpawn = 0L }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        val n = max(1, players.size)
        val gap = (900f / (1f + 0.35f * (n - 1))).toLong()
        if (now - lastSpawn > gap && bubbles.size < 5 + 2 * n) {
            val gold = Random.nextFloat() < 0.12f
            bubbles.add(B(0.08f + Random.nextFloat() * 0.84f, 0.2f + Random.nextFloat() * 0.6f, now, gold))
            lastSpawn = now
        }
        bubbles.removeAll { now - it.born > (if (it.gold) 2500L else 5000L) }
        val r = min(w, h) * 0.075f
        val iter = bubbles.iterator()
        loop@ while (iter.hasNext()) {
            val b = iter.next()
            val rr = if (b.gold) r * 0.75f else r
            val bx = b.x * w; val by = b.y * h
            for (p in players) for (i in HAND_IDX) {
                if (i < p.n && hypot(p.x(i, w) - bx, p.y(i, h) - by) < rr * 1.25f) {
                    scores[p.slot] += if (b.gold) 5 else 1
                    Sfx.pop()
                    Fx.burst(bx, by, if (b.gold) Color.rgb(255, 215, 60) else Color.rgb(120, 220, 255), if (b.gold) 26 else 14, 0.5f, h * 0.008f)
                    Fx.text(bx, by - rr, if (b.gold) "+5" else "+1", PLAYER_COLORS[p.slot], b.gold)
                    iter.remove(); continue@loop
                }
            }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = GameClock.now()
        val r = min(w, h) * 0.075f
        for (b in bubbles) {
            val age = now - b.born
            val life = if (b.gold) 2500f else 5000f
            val fade = 1f - ((age - (life - 800f)) / 800f).coerceIn(0f, 1f)
            val rr = (if (b.gold) r * 0.75f else r) * min(1f, age / 180f)
            val bx = b.x * w
            val by = b.y * h + sin(age / 400f + b.x * 10f) * h * 0.008f
            val col = if (b.gold) Color.rgb(255, 205, 40) else Color.rgb(60, 200, 255)
            paint.color = withAlpha(col, (45 * fade).toInt()); c.drawCircle(bx, by, rr * 1.35f, paint)
            paint.color = withAlpha(col, (140 * fade).toInt()); c.drawCircle(bx, by, rr, paint)
            paint.color = withAlpha(lighten(col, 0.5f), (90 * fade).toInt()); c.drawCircle(bx - rr * 0.1f, by - rr * 0.1f, rr * 0.78f, paint)
            edge.color = withAlpha(Color.WHITE, (210 * fade).toInt()); c.drawCircle(bx, by, rr, edge)
            paint.color = withAlpha(Color.WHITE, (220 * fade).toInt())
            c.drawCircle(bx - rr * 0.38f, by - rr * 0.4f, rr * 0.18f, paint)
            c.drawCircle(bx - rr * 0.2f, by - rr * 0.55f, rr * 0.07f, paint)
            if (b.gold) { txt.textSize = rr; txt.color = withAlpha(Color.WHITE, (255 * fade).toInt()); c.drawText("5", bx, by + rr * 0.35f, txt) }
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        val spots = arrayOf(floatArrayOf(-0.55f, 0.25f, 0.55f), floatArrayOf(0.4f, 0.4f, 0.45f), floatArrayOf(0f, -0.45f, 0.4f))
        for (b in spots) {
            val x = cx + b[0] * s; val y = cy + b[1] * s; val r = b[2] * s
            ip.color = withAlpha(Color.rgb(90, 210, 255), 160); c.drawCircle(x, y, r, ip)
            ip.color = withAlpha(Color.WHITE, 230); c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.2f, ip)
        }
    }
}

// ======================= 2. Fruit Slice =======================
class FruitGame : Game("fruit", "Fruit Slice", "Swipe fast through flying fruit. Bombs cost 5 points!") {
    override val hint = "Slice fruit, avoid bombs"
    override val color = Color.rgb(255, 140, 0)
    override val bgTop = Color.rgb(46, 22, 14)
    override val bgBottom = Color.rgb(120, 52, 22)
    private class F(var x: Float, var y: Float, val vx: Float, var vy: Float, val type: Int, val bomb: Boolean, var rot: Float, val rotV: Float)
    private class Pc(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val rotV: Float, val type: Int, val half: Int, val born: Long)
    private class Tr(val ax: Float, val ay: Float, val bx: Float, val by: Float, val born: Long, val color: Int)
    private val fruits = ArrayList<F>()
    private val pieces = ArrayList<Pc>()
    private val trails = ArrayList<Tr>()
    private val prev = Array(4) { FloatArray(HAND_IDX.size * 2) { -1f } }
    private val lastTs = LongArray(4)
    private var lastSpawn = 0L
    private val skin = intArrayOf(Color.rgb(255, 140, 0), Color.rgb(220, 40, 50), Color.rgb(60, 170, 70), Color.rgb(250, 220, 50), Color.rgb(140, 70, 190))
    private val flesh = intArrayOf(Color.rgb(255, 195, 100), Color.rgb(255, 245, 205), Color.rgb(240, 70, 90), Color.rgb(255, 248, 160), Color.rgb(205, 150, 235))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val ip = iconFill()
    private val il = iconLine()

    override fun onReset(now: Long) {
        fruits.clear(); pieces.clear(); trails.clear(); lastSpawn = 0L; lastTs.fill(0L); for (a in prev) a.fill(-1f)
    }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        val n = max(1, players.size)
        val g = h * 0.0000022f
        val gap = max(250, 520 - 90 * (n - 1)).toLong()
        if (now - lastSpawn > gap) {
            val bomb = Random.nextFloat() < 0.14f
            val apex = h * (0.55f + Random.nextFloat() * 0.3f)
            fruits.add(F(w * (0.1f + 0.8f * Random.nextFloat()), h * 1.05f, w * (Random.nextFloat() - 0.5f) * 0.00024f,
                -sqrt(2f * g * apex), Random.nextInt(5), bomb, 0f, (Random.nextFloat() - 0.5f) * 0.4f))
            lastSpawn = now
        }
        val d = dt.toFloat()
        for (f in fruits) { f.vy += g * d; f.x += f.vx * d; f.y += f.vy * d; f.rot += f.rotV * d }
        fruits.removeAll { it.y > h * 1.15f && it.vy > 0f }
        for (p in pieces) { p.vy += g * d; p.x += p.vx * d; p.y += p.vy * d; p.rot += p.rotV * d }
        pieces.removeAll { it.y > h * 1.2f || now - it.born > 2500L }
        trails.removeAll { now - it.born > 250L }

        val r = min(w, h) * 0.07f
        for (p in players) {
            if (p.ts == lastTs[p.slot]) continue
            val frameDt = p.ts - lastTs[p.slot]
            val first = lastTs[p.slot] == 0L
            lastTs[p.slot] = p.ts
            val pv = prev[p.slot]
            for ((k, i) in HAND_IDX.withIndex()) {
                if (i >= p.n) continue
                val hx = p.x(i, w); val hy = p.y(i, h)
                val px = pv[k * 2]; val py = pv[k * 2 + 1]
                pv[k * 2] = hx; pv[k * 2 + 1] = hy
                if (first || px < 0f || frameDt <= 0L) continue
                val speed = hypot(hx - px, hy - py) / frameDt
                if (speed < h * 0.0005f) continue
                if (trails.size < 80) trails.add(Tr(px, py, hx, hy, now, PLAYER_COLORS[p.slot]))
                val ang = Math.toDegrees(atan2((hy - py).toDouble(), (hx - px).toDouble())).toFloat()
                var idx = fruits.size - 1
                while (idx >= 0) {
                    val f = fruits[idx]
                    if (segDist(f.x, f.y, px, py, hx, hy) < r * 1.3f) {
                        if (f.bomb) {
                            scores[p.slot] = max(0, scores[p.slot] - 5); Sfx.hit()
                            Fx.burst(f.x, f.y, Color.rgb(255, 120, 30), 30, 0.7f, h * 0.012f)
                            Fx.shake(350f, h * 0.014f)
                            Fx.text(f.x, f.y, "-5", Color.RED, true)
                        } else {
                            scores[p.slot] += 1; Sfx.pop()
                            Fx.burst(f.x, f.y, skin[f.type], 16, 0.45f, h * 0.009f)
                            Fx.text(f.x, f.y - r, "+1", PLAYER_COLORS[p.slot])
                            pieces.add(Pc(f.x, f.y, f.vx - 0.12f * (hy - py) / max(1f, hypot(hx - px, hy - py)), f.vy + 0.12f * (hx - px) / max(1f, hypot(hx - px, hy - py)), ang, 0.2f, f.type, 0, now))
                            pieces.add(Pc(f.x, f.y, f.vx + 0.12f * (hy - py) / max(1f, hypot(hx - px, hy - py)), f.vy - 0.12f * (hx - px) / max(1f, hypot(hx - px, hy - py)), ang, -0.2f, f.type, 1, now))
                        }
                        fruits.removeAt(idx)
                    }
                    idx--
                }
            }
        }
    }

    private fun drawFruit(c: Canvas, type: Int, r: Float) {
        paint.color = skin[type]
        when (type) {
            0 -> { c.drawCircle(0f, 0f, r, paint); paint.color = Color.rgb(60, 170, 70); c.drawOval(RectF(r * 0.05f, -r * 1.2f, r * 0.55f, -r * 0.8f), paint) }
            1 -> { c.drawCircle(0f, 0f, r, paint); line.color = Color.rgb(110, 70, 30); line.strokeWidth = r * 0.12f; c.drawLine(0f, -r * 0.8f, r * 0.1f, -r * 1.2f, line)
                   paint.color = Color.rgb(60, 170, 70); c.drawOval(RectF(r * 0.1f, -r * 1.25f, r * 0.6f, -r * 0.95f), paint) }
            2 -> { c.drawCircle(0f, 0f, r, paint); line.color = Color.rgb(25, 100, 40); line.strokeWidth = r * 0.14f
                   c.drawOval(RectF(-r * 0.55f, -r, r * 0.55f, r), line); c.drawLine(0f, -r, 0f, r, line) }
            3 -> { c.drawOval(RectF(-r * 1.2f, -r * 0.8f, r * 1.2f, r * 0.8f), paint); c.drawCircle(r * 1.2f, 0f, r * 0.14f, paint); c.drawCircle(-r * 1.2f, 0f, r * 0.14f, paint) }
            else -> { for (k in 0 until 7) { val ox = ((k % 3) - 1) * r * 0.55f; val oy = (k / 3 - 0.6f) * r * 0.6f + (if (k == 6) r * 0.6f else 0f)
                      paint.color = skin[4]; c.drawCircle(ox, oy, r * 0.42f, paint)
                      paint.color = withAlpha(Color.WHITE, 120); c.drawCircle(ox - r * 0.12f, oy - r * 0.12f, r * 0.1f, paint) } }
        }
        paint.color = withAlpha(Color.WHITE, 90); c.drawCircle(-r * 0.35f, -r * 0.35f, r * 0.2f, paint)
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = GameClock.now()
        val r = min(w, h) * 0.07f
        for (f in fruits) {
            c.save(); c.translate(f.x, f.y); c.rotate(f.rot)
            if (f.bomb) {
                paint.color = Color.rgb(35, 35, 45); c.drawCircle(0f, 0f, r, paint)
                paint.color = Color.rgb(80, 80, 98); c.drawCircle(-r * 0.3f, -r * 0.3f, r * 0.25f, paint)
                line.color = withAlpha(Color.RED, 140 + (100 * sin(now / 120f)).toInt()); line.strokeWidth = r * 0.1f; c.drawCircle(0f, 0f, r, line)
                line.color = Color.rgb(120, 85, 40); line.strokeWidth = r * 0.1f; c.drawLine(r * 0.3f, -r * 0.9f, r * 0.55f, -r * 1.25f, line)
                paint.color = if ((now / 120L) % 2L == 0L) Color.rgb(255, 170, 40) else Color.rgb(255, 240, 90)
                c.drawCircle(r * 0.58f, -r * 1.3f, r * 0.2f, paint)
            } else drawFruit(c, f.type, r)
            c.restore()
        }
        for (p in pieces) {
            c.save(); c.translate(p.x, p.y); c.rotate(p.rot)
            val start = if (p.half == 0) 180f else 0f
            paint.color = skin[p.type]; c.drawArc(RectF(-r, -r, r, r), start, 180f, true, paint)
            val ri = r * 0.82f
            paint.color = flesh[p.type]; c.drawArc(RectF(-ri, -ri, ri, ri), start, 180f, true, paint)
            c.restore()
        }
        for (t in trails) {
            val a = (1f - (now - t.born) / 250f).coerceIn(0f, 1f)
            line.color = withAlpha(t.color, (110 * a).toInt()); line.strokeWidth = 22f * a + 4f; c.drawLine(t.ax, t.ay, t.bx, t.by, line)
            line.color = withAlpha(Color.WHITE, (230 * a).toInt()); line.strokeWidth = 7f * a + 2f; c.drawLine(t.ax, t.ay, t.bx, t.by, line)
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        ip.color = Color.rgb(255, 140, 0); c.drawCircle(cx - 0.1f * s, cy + 0.1f * s, 0.55f * s, ip)
        ip.color = Color.rgb(60, 170, 70); c.drawOval(RectF(cx - 0.05f * s, cy - 0.65f * s, cx + 0.4f * s, cy - 0.35f * s), ip)
        ip.color = withAlpha(Color.WHITE, 120); c.drawCircle(cx - 0.3f * s, cy - 0.1f * s, 0.12f * s, ip)
        il.strokeWidth = s * 0.1f; c.drawLine(cx - 0.8f * s, cy - 0.7f * s, cx + 0.7f * s, cy + 0.8f * s, il)
    }
}

// ======================= 3. Meteor Dodge =======================
class DodgeGame : Game("dodge", "Meteor Dodge", "Dodge falling meteors. 3 lives. Last one standing wins!") {
    override val hint = "Dodge the meteors"
    override val color = Color.rgb(170, 70, 200)
    override val bgTop = Color.rgb(8, 6, 28)
    override val bgBottom = Color.rgb(58, 18, 70)
    override val roundMs = 0L
    override val joinLate = false
    private class M(val x: Float, var y: Float, val vy: Float, val r: Float)
    private val ms = ArrayList<M>()
    private val lives = IntArray(4)
    private val alive = BooleanArray(4)
    private val invUntil = LongArray(4)
    private var lastSpawn = 0L
    private var startAt = 0L
    private var flashUntil = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }
    private val body = intArrayOf(0, 11, 12, 15, 16, 23, 24, 25, 26)
    private val ip = iconFill()

    override fun onReset(now: Long) { ms.clear(); lives.fill(3); alive.fill(false); invUntil.fill(0L); lastSpawn = 0L; flashUntil = 0L }

    override fun onStart(now: Long) {
        startAt = now
        for (i in 0 until 4) { alive[i] = joined[i]; lives[i] = 3 }
    }

    override fun status(slot: Int): String =
        if (!joined[slot]) "" else if (alive[slot]) "${scores[slot]}s" else "OUT ${scores[slot]}s"

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        val el = now - startAt
        val n = max(1, players.size)
        val gap = (max(240L, 750L - el / 118L) / (1f + 0.2f * (n - 1))).toLong()
        if (now - lastSpawn > gap) {
            val r = min(w, h) * (0.035f + 0.04f * Random.nextFloat())
            ms.add(M(w * Random.nextFloat(), -r, h * (0.00035f + 0.0003f * Random.nextFloat()) * (1f + el / 120000f), r))
            lastSpawn = now
        }
        for (m in ms) m.y += m.vy * dt
        ms.removeAll { it.y > h + it.r }

        for (s in 0 until 4) if (alive[s]) scores[s] = (el / 1000L).toInt()

        for (p in players) {
            val s = p.slot
            if (!alive[s] || now < invUntil[s]) continue
            var idx = ms.size - 1
            var hit = false
            while (idx >= 0 && !hit) {
                val m = ms[idx]
                for (i in body) {
                    if (i < p.n && hypot(p.x(i, w) - m.x, p.y(i, h) - m.y) < m.r + 12f) { hit = true; break }
                }
                if (hit) {
                    Fx.burst(m.x, m.y, Color.rgb(255, 150, 40), 28, 0.6f, h * 0.011f)
                    Fx.text(p.x(0, w), p.y(0, h) - h * 0.1f, "OUCH!", Color.rgb(255, 90, 90), true)
                    ms.removeAt(idx)
                }
                idx--
            }
            if (hit) {
                lives[s]--; invUntil[s] = now + 1500L; Sfx.hit()
                Fx.shake(320f, h * 0.014f); flashUntil = now + 250L
                if (lives[s] <= 0) alive[s] = false
            }
        }
        if (el > 1500L && joined.any { it } && (0 until 4).none { alive[it] }) finish()
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = GameClock.now()
        for (m in ms) {
            for (k in 5 downTo 1) {
                paint.color = withAlpha(Color.rgb(255, 140, 40), 20 + (6 - k) * 8)
                c.drawCircle(m.x, m.y - k * m.r * 0.8f, m.r * (1f - k * 0.12f), paint)
            }
            paint.color = withAlpha(Color.rgb(255, 120, 0), 70); c.drawCircle(m.x, m.y, m.r * 1.6f, paint)
            paint.color = Color.rgb(125, 72, 46); c.drawCircle(m.x, m.y, m.r, paint)
            ring.color = Color.rgb(255, 160, 60); ring.strokeWidth = m.r * 0.12f; c.drawCircle(m.x, m.y, m.r, ring)
            paint.color = Color.rgb(90, 50, 32)
            c.drawCircle(m.x - m.r * 0.3f, m.y - m.r * 0.2f, m.r * 0.25f, paint)
            c.drawCircle(m.x + m.r * 0.35f, m.y + m.r * 0.25f, m.r * 0.18f, paint)
            c.drawCircle(m.x + m.r * 0.1f, m.y - m.r * 0.5f, m.r * 0.12f, paint)
        }
        for (p in players) {
            if (phase == 1 && alive[p.slot] && now < invUntil[p.slot] && (now / 150L) % 2L == 0L && p.n > 24) {
                ring.color = PLAYER_COLORS[p.slot]; ring.strokeWidth = 8f
                c.drawCircle(p.x(23, w) * 0.5f + p.x(24, w) * 0.5f, p.y(23, h) * 0.5f + p.y(24, h) * 0.5f, h * 0.2f, ring)
            }
        }
        if (now < flashUntil) { paint.color = withAlpha(Color.RED, (90 * (flashUntil - now) / 250f).toInt()); c.drawRect(0f, 0f, w, h, paint) }
    }

    override fun drawOverlay(c: Canvas, w: Float, h: Float) {
        for (s in 0 until 4) if (joined[s] && phase >= 1) {
            val pr = pillRect(s, w, h)
            for (i in 0 until 3) {
                paint.color = if (i < lives[s]) Color.rgb(255, 70, 90) else withAlpha(Color.WHITE, 70)
                drawHeart(c, pr.right - 34f - (2 - i) * h * 0.05f, pr.centerY(), h * 0.02f, paint)
            }
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        for (k in 4 downTo 1) { ip.color = withAlpha(Color.rgb(255, 150, 50), 30 + (5 - k) * 12); c.drawCircle(cx - k * 0.22f * s, cy - k * 0.22f * s, 0.34f * s * (1f - k * 0.1f), ip) }
        ip.color = Color.rgb(125, 72, 46); c.drawCircle(cx + 0.2f * s, cy + 0.2f * s, 0.4f * s, ip)
        ip.color = Color.rgb(90, 50, 32); c.drawCircle(cx + 0.08f * s, cy + 0.12f * s, 0.1f * s, ip)
        ip.color = withAlpha(Color.rgb(255, 160, 60), 200); c.drawCircle(cx + 0.35f * s, cy + 0.3f * s, 0.07f * s, ip)
    }
}

// ======================= 4 & 5. Fitness: Squat Race / Jumping Jacks =======================
class FitnessGame(private val jacks: Boolean) : Game(
    if (jacks) "jacks" else "squats",
    if (jacks) "Jumping Jacks" else "Squat Race",
    if (jacks) "Most jumping jacks in 60 seconds" else "Most squats in 60 seconds. Show your knees!"
) {
    override val hint = if (jacks) "Do jumping jacks" else "Do squats - show your knees"
    override val color = if (jacks) Color.rgb(230, 80, 150) else Color.rgb(60, 190, 110)
    override val bgTop = if (jacks) Color.rgb(40, 8, 40) else Color.rgb(6, 36, 30)
    override val bgBottom = if (jacks) Color.rgb(110, 24, 80) else Color.rgb(16, 96, 70)
    private val state = BooleanArray(4)
    private val base = FloatArray(4)
    private val warn = BooleanArray(4)
    private val popAt = LongArray(4)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.WHITE }
    private val il = iconLine()

    override fun onReset(now: Long) { state.fill(false); base.fill(0f); warn.fill(false); popAt.fill(0L) }

    private fun count(s: Int, p: Player, now: Long, w: Float, h: Float) {
        scores[s]++; Sfx.pop(); popAt[s] = now
        Fx.burst(p.x(0, w), p.y(0, h), PLAYER_COLORS[s], 14, 0.45f, h * 0.008f)
    }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        for (p in players) {
            if (p.n < 29) continue
            val s = p.slot
            if (jacks) {
                val noseY = p.rawY(0)
                val shY = (p.rawY(11) + p.rawY(12)) / 2f
                val sw = abs(p.rawX(11) - p.rawX(12))
                if (sw < 0.02f) continue
                val armsUp = p.rawY(15) < noseY && p.rawY(16) < noseY
                val armsDown = p.rawY(15) > shY && p.rawY(16) > shY
                val ankles = p.rawY(27) < 0.97f && p.rawY(28) < 0.97f
                val legsApart = !ankles || abs(p.rawX(27) - p.rawX(28)) > 1.2f * sw
                if (!state[s] && armsUp && legsApart) { state[s] = true; count(s, p, now, w, h) }
                else if (state[s] && armsDown) state[s] = false
            } else {
                warn[s] = !(p.rawY(25) < 0.97f && p.rawY(26) < 0.97f)
                if (warn[s]) continue
                val sy = (p.rawY(11) + p.rawY(12)) / 2f
                val hy = (p.rawY(23) + p.rawY(24)) / 2f
                val ky = (p.rawY(25) + p.rawY(26)) / 2f
                val torso = abs(hy - sy)
                if (torso < 0.04f) continue
                val ratio = (ky - hy) / torso
                if (ratio > base[s]) base[s] += (ratio - base[s]) * min(1f, dt / 300f)
                else base[s] += (ratio - base[s]) * min(1f, dt / 20000f)
                if (base[s] < 0.4f) continue
                if (!state[s] && ratio < base[s] * 0.72f) state[s] = true
                else if (state[s] && ratio > base[s] * 0.88f) { state[s] = false; count(s, p, now, w, h) }
            }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = GameClock.now()
        for (p in players) {
            if (!joined[p.slot]) continue
            val s = p.slot
            val pop = 1f + 0.35f * (1f - ((now - popAt[s]) / 250f).coerceIn(0f, 1f))
            val rr = h * 0.075f * pop
            val cx = p.x(0, w); val cy = max(h * 0.2f, p.y(0, h) - h * 0.16f)
            fillP.color = withAlpha(PLAYER_COLORS[s], 90); c.drawCircle(cx, cy, rr * 1.3f, fillP)
            fillP.color = withAlpha(PLAYER_COLORS[s], 230); c.drawCircle(cx, cy, rr, fillP)
            c.drawCircle(cx, cy, rr, strokeP)
            txt.color = Color.WHITE; txt.textSize = rr * 1.1f
            c.drawText(scores[s].toString(), cx, cy + rr * 0.38f, txt)
            if (!jacks && warn[s]) {
                val tw = h * 0.045f
                txt.textSize = tw
                fillP.color = withAlpha(Color.BLACK, 150); c.drawRoundRect(RectF(cx - tw * 3f, cy + rr * 1.4f, cx + tw * 3f, cy + rr * 1.4f + tw * 1.6f), 20f, 20f, fillP)
                txt.color = PLAYER_COLORS[s]; c.drawText("Step back", cx, cy + rr * 1.4f + tw * 1.15f, txt)
            }
        }
    }

    override fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        il.strokeWidth = s * 0.14f
        fillP.color = Color.WHITE
        if (jacks) {
            c.drawCircle(cx, cy - 0.72f * s, 0.2f * s, fillP)
            c.drawLine(cx, cy - 0.5f * s, cx, cy + 0.1f * s, il)
            c.drawLine(cx, cy - 0.42f * s, cx - 0.6f * s, cy - 0.95f * s, il); c.drawLine(cx, cy - 0.42f * s, cx + 0.6f * s, cy - 0.95f * s, il)
            c.drawLine(cx, cy + 0.1f * s, cx - 0.45f * s, cy + 0.75f * s, il); c.drawLine(cx, cy + 0.1f * s, cx + 0.45f * s, cy + 0.75f * s, il)
        } else {
            c.drawCircle(cx - 0.1f * s, cy - 0.72f * s, 0.2f * s, fillP)
            c.drawLine(cx - 0.1f * s, cy - 0.5f * s, cx - 0.1f * s, cy - 0.05f * s, il)
            c.drawLine(cx - 0.1f * s, cy - 0.05f * s, cx + 0.45f * s, cy + 0.1f * s, il); c.drawLine(cx + 0.45f * s, cy + 0.1f * s, cx + 0.35f * s, cy + 0.75f * s, il)
            c.drawLine(cx - 0.1f * s, cy - 0.4f * s, cx + 0.5f * s, cy - 0.4f * s, il)
        }
    }
}
