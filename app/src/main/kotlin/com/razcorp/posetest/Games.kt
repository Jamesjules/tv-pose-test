package com.razcorp.posetest

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

private val HAND_IDX = intArrayOf(15, 16, 19, 20)

// ======================= 1. Bubble Pop =======================
class BubbleGame : Game("bubble", "Bubble Pop", "Pop bubbles with your hands. Gold bubbles are worth 5!") {
    override val hint = "Pop the bubbles! Gold = 5 points"
    override val color = Color.rgb(0, 170, 230)
    private class B(val x: Float, val y: Float, val born: Long, val gold: Boolean)
    private val bubbles = ArrayList<B>()
    private var lastSpawn = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.WHITE }

    override fun onReset(now: Long) { bubbles.clear(); lastSpawn = 0L }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        val n = max(1, players.size)
        val gap = (900f / (1f + 0.35f * (n - 1))).toLong()
        if (now - lastSpawn > gap && bubbles.size < 5 + 2 * n) {
            val gold = Random.nextFloat() < 0.12f
            bubbles.add(B(0.08f + Random.nextFloat() * 0.84f, 0.18f + Random.nextFloat() * 0.62f, now, gold))
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
                    Sfx.pop(); iter.remove(); continue@loop
                }
            }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = SystemClock.elapsedRealtime()
        val r = min(w, h) * 0.075f
        for (b in bubbles) {
            val life = if (b.gold) 2500f else 5000f
            val a = (255 * (1f - ((now - b.born) / life).coerceIn(0f, 1f) * 0.6f)).toInt()
            paint.color = if (b.gold) Color.rgb(255, 200, 40) else Color.rgb(0, 190, 255)
            paint.alpha = a
            val rr = if (b.gold) r * 0.75f else r
            c.drawCircle(b.x * w, b.y * h, rr, paint)
            c.drawCircle(b.x * w, b.y * h, rr, edge)
        }
    }
}

// ======================= 2. Fruit Slice =======================
class FruitGame : Game("fruit", "Fruit Slice", "Swipe fast through flying fruit. Bombs cost 5 points!") {
    override val hint = "Slice the fruit - avoid the bombs!"
    override val color = Color.rgb(255, 140, 0)
    private class F(var x: Float, var y: Float, val vx: Float, var vy: Float, val color: Int, val bomb: Boolean)
    private class S(val x: Float, val y: Float, val color: Int, val born: Long)
    private val fruits = ArrayList<F>()
    private val splashes = ArrayList<S>()
    private val prev = Array(4) { FloatArray(HAND_IDX.size * 2) { -1f } }
    private val lastTs = LongArray(4)
    private var lastSpawn = 0L
    private val cols = intArrayOf(Color.rgb(255, 140, 0), Color.rgb(230, 50, 60), Color.rgb(90, 200, 70), Color.rgb(250, 220, 50), Color.rgb(170, 80, 220))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }

    override fun onReset(now: Long) { fruits.clear(); splashes.clear(); lastSpawn = 0L; lastTs.fill(0L); for (a in prev) a.fill(-1f) }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        val n = max(1, players.size)
        val g = h * 0.0000022f
        val gap = max(250, 520 - 90 * (n - 1)).toLong()
        if (now - lastSpawn > gap) {
            val bomb = Random.nextFloat() < 0.14f
            val apex = h * (0.55f + Random.nextFloat() * 0.3f)
            fruits.add(F(w * (0.1f + 0.8f * Random.nextFloat()), h * 1.05f, w * (Random.nextFloat() - 0.5f) * 0.00024f,
                -sqrt(2f * g * apex), cols[Random.nextInt(cols.size)], bomb))
            lastSpawn = now
        }
        val d = dt.toFloat()
        for (f in fruits) { f.vy += g * d; f.x += f.vx * d; f.y += f.vy * d }
        fruits.removeAll { it.y > h * 1.15f && it.vy > 0f }
        splashes.removeAll { now - it.born > 350L }

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
                if (speed < h * 0.0008f) continue
                var idx = fruits.size - 1
                while (idx >= 0) {
                    val f = fruits[idx]
                    if (segDist(f.x, f.y, px, py, hx, hy) < r * 1.3f) {
                        if (f.bomb) { scores[p.slot] = max(0, scores[p.slot] - 5); Sfx.hit(); splashes.add(S(f.x, f.y, Color.RED, now)) }
                        else { scores[p.slot] += 1; Sfx.pop(); splashes.add(S(f.x, f.y, f.color, now)) }
                        fruits.removeAt(idx)
                    }
                    idx--
                }
            }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = SystemClock.elapsedRealtime()
        val r = min(w, h) * 0.07f
        for (f in fruits) {
            if (f.bomb) {
                paint.color = Color.rgb(40, 40, 45); c.drawCircle(f.x, f.y, r, paint)
                ring.color = Color.RED; c.drawCircle(f.x, f.y, r, ring)
                paint.color = Color.rgb(255, 160, 0); c.drawCircle(f.x + r * 0.5f, f.y - r * 0.9f, r * 0.15f, paint)
            } else {
                paint.color = f.color; c.drawCircle(f.x, f.y, r, paint)
                paint.color = Color.argb(90, 255, 255, 255); c.drawCircle(f.x - r * 0.3f, f.y - r * 0.3f, r * 0.3f, paint)
            }
        }
        for (s in splashes) {
            val t = (now - s.born) / 350f
            ring.color = s.color; ring.alpha = (255 * (1f - t)).toInt()
            c.drawCircle(s.x, s.y, r * (0.6f + 1.6f * t), ring)
        }
    }
}

// ======================= 3. Meteor Dodge =======================
class DodgeGame : Game("dodge", "Meteor Dodge", "Dodge falling meteors. 3 lives. Last one standing wins!") {
    override val hint = "Dodge the meteors!"
    override val color = Color.rgb(170, 70, 200)
    override val roundMs = 0L
    override val joinLate = false
    private class M(val x: Float, var y: Float, val vy: Float, val r: Float)
    private val ms = ArrayList<M>()
    private val lives = IntArray(4)
    private val alive = BooleanArray(4)
    private val invUntil = LongArray(4)
    private var lastSpawn = 0L
    private var startAt = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }
    private val body = intArrayOf(0, 11, 12, 15, 16, 23, 24, 25, 26)

    override fun onReset(now: Long) { ms.clear(); lives.fill(3); alive.fill(false); invUntil.fill(0L); lastSpawn = 0L }

    override fun onStart(now: Long) {
        startAt = now
        for (i in 0 until 4) { alive[i] = joined[i]; lives[i] = 3 }
    }

    override fun status(slot: Int): String =
        if (!joined[slot]) "" else if (alive[slot]) "${scores[slot]}s  lives ${lives[slot]}" else "OUT ${scores[slot]}s"

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
                if (hit) ms.removeAt(idx)
                idx--
            }
            if (hit) {
                lives[s]--; invUntil[s] = now + 1500L; Sfx.hit()
                if (lives[s] <= 0) alive[s] = false
            }
        }
        if (el > 1500L && joined.any { it } && (0 until 4).none { alive[it] }) finish()
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        val now = SystemClock.elapsedRealtime()
        for (m in ms) {
            paint.color = Color.argb(110, 255, 120, 0); c.drawCircle(m.x, m.y, m.r * 1.5f, paint)
            paint.color = Color.rgb(150, 80, 40); c.drawCircle(m.x, m.y, m.r, paint)
            paint.color = Color.rgb(100, 55, 30); c.drawCircle(m.x - m.r * 0.25f, m.y - m.r * 0.2f, m.r * 0.3f, paint)
        }
        for (p in players) {
            if (phase == 1 && alive[p.slot] && now < invUntil[p.slot] && (now / 150L) % 2L == 0L && p.n > 24) {
                ring.color = PLAYER_COLORS[p.slot]
                c.drawCircle(p.x(23, w) * 0.5f + p.x(24, w) * 0.5f, p.y(23, h) * 0.5f + p.y(24, h) * 0.5f, h * 0.2f, ring)
            }
        }
    }
}

// ======================= 4 & 5. Fitness: Squat Race / Jumping Jacks =======================
class FitnessGame(private val jacks: Boolean) : Game(
    if (jacks) "jacks" else "squats",
    if (jacks) "Jumping Jacks" else "Squat Race",
    if (jacks) "Most jumping jacks in 60 seconds. Show your full body!" else "Most squats in 60 seconds. Show your full body!"
) {
    override val hint = if (jacks) "Jumping jacks! Arms up, legs wide" else "Squats! Stand back so your knees are visible"
    override val color = if (jacks) Color.rgb(230, 80, 150) else Color.rgb(60, 190, 110)
    private val state = BooleanArray(4)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; setShadowLayer(6f, 3f, 3f, Color.BLACK) }

    override fun onReset(now: Long) { state.fill(false) }

    override fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float) {
        for (p in players) {
            if (p.n < 29) continue
            val s = p.slot
            if (jacks) {
                val sw = abs(p.rawX(11) - p.rawX(12))
                if (sw < 0.03f) continue
                val armsUp = p.rawY(15) < p.rawY(0) && p.rawY(16) < p.rawY(0)
                val armsDown = p.rawY(15) > p.rawY(11) && p.rawY(16) > p.rawY(11)
                val legs = abs(p.rawX(27) - p.rawX(28))
                if (!state[s] && armsUp && legs > 1.7f * sw) { state[s] = true; scores[s]++; Sfx.pop() }
                else if (state[s] && armsDown && legs < 1.3f * sw) state[s] = false
            } else {
                val sy = (p.rawY(11) + p.rawY(12)) / 2f
                val hy = (p.rawY(23) + p.rawY(24)) / 2f
                val ky = (p.rawY(25) + p.rawY(26)) / 2f
                val torso = abs(hy - sy)
                if (torso < 0.05f) continue
                val ratio = (ky - hy) / torso
                if (!state[s] && ratio < 0.62f) state[s] = true
                else if (state[s] && ratio > 0.92f) { state[s] = false; scores[s]++; Sfx.pop() }
            }
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, players: List<Player>) {
        txt.textSize = h * 0.13f
        for (p in players) {
            if (!joined[p.slot]) continue
            txt.color = PLAYER_COLORS[p.slot]
            c.drawText(scores[p.slot].toString(), p.x(0, w), max(h * 0.16f, p.y(0, h) - h * 0.1f), txt)
        }
    }
}
