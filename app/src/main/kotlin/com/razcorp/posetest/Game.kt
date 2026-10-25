package com.razcorp.posetest

import android.graphics.Canvas
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import kotlin.math.hypot

val PLAYER_COLORS = intArrayOf(Color.rgb(255, 90, 90), Color.rgb(80, 160, 255), Color.rgb(90, 230, 120), Color.rgb(255, 210, 60))
val PLAYER_NAMES = arrayOf("P1", "P2", "P3", "P4")

/** One tracked person. Landmark indices: 0 nose, 11/12 shoulders, 13/14 elbows, 15/16 wrists, 19/20 fingers, 23/24 hips, 25/26 knees, 27/28 ankles. */
class Player(val slot: Int, val pts: FloatArray, val ts: Long) {
    val n: Int get() = pts.size / 2
    fun rawX(i: Int) = pts[i * 2]
    fun rawY(i: Int) = pts[i * 2 + 1]
    /** Screen coords, mirrored like a mirror. */
    fun x(i: Int, w: Float) = (1f - pts[i * 2]) * w
    fun y(i: Int, h: Float) = pts[i * 2 + 1] * h
}

fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val l2 = dx * dx + dy * dy
    val t = if (l2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/** Keeps each person in the same slot (P1..P4) from frame to frame and smooths jitter. */
class Tracker {
    private class Track(var pts: FloatArray, var cx: Float, var cy: Float, var seen: Long)
    private val tracks = arrayOfNulls<Track>(4)

    private fun center(p: FloatArray): Pair<Float, Float> {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in intArrayOf(11, 12, 23, 24)) if (i * 2 + 1 < p.size) { sx += p[i * 2]; sy += p[i * 2 + 1]; n++ }
        return if (n == 0) Pair(0.5f, 0.5f) else Pair(sx / n, sy / n)
    }

    fun ingest(poses: List<FloatArray>, now: Long) {
        val centers = poses.map { center(it) }
        val mt = BooleanArray(4)
        val mp = BooleanArray(poses.size)
        while (true) {
            var bi = -1; var bj = -1; var bd = 0.3f
            for (i in 0 until 4) {
                val t = tracks[i] ?: continue
                if (mt[i]) continue
                for (j in poses.indices) {
                    if (mp[j]) continue
                    val d = hypot(t.cx - centers[j].first, t.cy - centers[j].second)
                    if (d < bd) { bd = d; bi = i; bj = j }
                }
            }
            if (bi < 0) break
            mt[bi] = true; mp[bj] = true
            val t = tracks[bi]!!
            val np = poses[bj]
            if (t.pts.size == np.size) {
                for (k in np.indices) t.pts[k] = t.pts[k] * 0.35f + np[k] * 0.65f
            } else {
                t.pts = np.copyOf()
            }
            t.cx = centers[bj].first; t.cy = centers[bj].second; t.seen = now
        }
        for (j in poses.indices) {
            if (mp[j]) continue
            val free = tracks.indexOfFirst { it == null }
            if (free >= 0) tracks[free] = Track(poses[j].copyOf(), centers[j].first, centers[j].second, now)
        }
        for (i in 0 until 4) {
            val t = tracks[i]
            if (t != null && now - t.seen > 1500) tracks[i] = null
        }
    }

    fun players(now: Long): List<Player> =
        (0 until 4).mapNotNull { i -> tracks[i]?.let { t -> if (now - t.seen < 500) Player(i, t.pts, t.seen) else null } }
}

object Sfx {
    private val tg: ToneGenerator? = try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (e: Throwable) { null }
    private fun beep(t: Int, ms: Int) { try { tg?.startTone(t, ms) } catch (_: Throwable) {} }
    fun pop() = beep(ToneGenerator.TONE_PROP_BEEP, 60)
    fun hit() = beep(ToneGenerator.TONE_PROP_NACK, 150)
    fun tick() = beep(ToneGenerator.TONE_PROP_ACK, 80)
    fun over() = beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
}

/** Base class: handles countdown, play phase, round timer. Games only implement update() and draw(). */
abstract class Game(val key: String, val title: String, val desc: String) {
    val scores = IntArray(4)
    val joined = BooleanArray(4)
    var phase = 0            // 0 countdown, 1 playing, 2 over
    var phaseStart = 0L
    private var last = 0L
    open val roundMs = 60000L    // 0 = no time limit
    open val joinLate = true
    open val color: Int = Color.rgb(90, 90, 90)
    abstract val hint: String

    fun reset(now: Long, players: List<Player>) {
        scores.fill(0); joined.fill(false)
        players.forEach { joined[it.slot] = true }
        phase = 0; phaseStart = now; last = now
        onReset(now)
    }

    fun tick(now: Long, players: List<Player>, w: Float, h: Float) {
        if (phase == 0) {
            players.forEach { joined[it.slot] = true }
            if (now - phaseStart >= 3000) { phase = 1; phaseStart = now; last = now; onStart(now) }
        } else if (phase == 1) {
            if (joinLate) players.forEach { joined[it.slot] = true }
            val dt = (now - last).coerceIn(0L, 100L)
            last = now
            update(now, dt, players, w, h)
            if (phase == 1 && roundMs > 0 && now - phaseStart >= roundMs) finish()
        }
    }

    fun finish() { if (phase == 1) { phase = 2; phaseStart = android.os.SystemClock.elapsedRealtime(); Sfx.over() } }

    open fun status(slot: Int): String = scores[slot].toString()
    open fun onReset(now: Long) {}
    open fun onStart(now: Long) {}
    abstract fun update(now: Long, dt: Long, players: List<Player>, w: Float, h: Float)
    abstract fun draw(c: Canvas, w: Float, h: Float, players: List<Player>)
}
