package com.razcorp.posetest

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View

/** TV screen: menu (raise a hand over a game, or remote), countdown, play, results. 1-4 players. */
class GameView(ctx: Context) : View(ctx) {
    private class Frame(val poses: List<FloatArray>, val ts: Long)
    @Volatile private var latest: Frame? = null
    private var seenTs = -1L
    @Volatile private var thumb: Bitmap? = null
    @Volatile private var thumbAt = 0L
    @Volatile var info: String = ""

    private val prefs = ctx.getSharedPreferences("posetest", 0)
    private val tracker = Tracker()
    private val games: List<Game> = listOf(BubbleGame(), FruitGame(), DodgeGame(), FitnessGame(false), FitnessGame(true))
    private var selected = 0
    private var current: Game? = null
    private var menuAt = 0L
    private val cardStart = LongArray(8)
    private var hoverIdx = -1
    private var debug = false
    private var savedResult = false
    private var newBest = false
    private var lastCount = -1

    private val bg = Paint().apply { color = Color.rgb(14, 20, 28) }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val prog = Paint().apply { color = Color.WHITE }
    private val bone = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 8f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(8f, 3f, 3f, Color.BLACK) }
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; setShadowLayer(5f, 2f, 2f, Color.BLACK) }
    private val bones = intArrayOf(11,12, 11,13, 13,15, 12,14, 14,16, 11,23, 12,24, 23,24, 23,25, 25,27, 24,26, 26,28)
    private val handIdx = intArrayOf(15, 16, 19, 20)

    fun setFrame(poses: List<FloatArray>) { latest = Frame(poses, SystemClock.elapsedRealtime()) }
    fun setThumb(b: Bitmap) { thumb = b; thumbAt = SystemClock.elapsedRealtime() }
    val inMenu: Boolean get() = current == null
    fun inGame(): Boolean = current != null

    fun backToMenu() { current = null; menuAt = SystemClock.elapsedRealtime(); cardStart.fill(0L) }

    private fun startGame(i: Int) {
        val now = SystemClock.elapsedRealtime()
        val g = games[i]
        selected = i; current = g
        g.reset(now, tracker.players(now))
        savedResult = false; newBest = false; lastCount = -1; cardStart.fill(0L)
    }

    fun onKey(k: Int): Boolean {
        val g = current
        val ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
        if (g == null) {
            return when (k) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { selected = (selected + games.size - 1) % games.size; true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { selected = (selected + 1) % games.size; true }
                KeyEvent.KEYCODE_DPAD_UP -> { debug = !debug; true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { startGame(selected); true }
                else -> false
            }
        }
        if (g.phase == 2 && ok) { startGame(games.indexOf(g)); return true }
        return false
    }

    private fun drawWrapped(c: Canvas, text: String, x: Float, y: Float, maxW: Float, p: Paint): Float {
        var line = ""; var yy = y
        for (word in text.split(" ")) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(t) > maxW && line.isNotEmpty()) { c.drawText(line, x, yy, p); yy += p.textSize * 1.25f; line = word } else line = t
        }
        if (line.isNotEmpty()) { c.drawText(line, x, yy, p); yy += p.textSize * 1.25f }
        return yy
    }

    /** Only RAISED hands count as a cursor (wrist above mid-torso), so a resting hand never selects anything. */
    private fun raisedHands(players: List<Player>, w: Float, h: Float): List<PointF> {
        val out = ArrayList<PointF>()
        for (p in players) {
            if (p.n < 25) continue
            for (s in arrayOf(intArrayOf(15, 19, 11, 23), intArrayOf(16, 20, 12, 24))) {
                if (p.rawY(s[0]) < (p.rawY(s[2]) + p.rawY(s[3])) / 2f) {
                    out.add(PointF(p.x(s[0], w), p.y(s[0], h)))
                    out.add(PointF(p.x(s[1], w), p.y(s[1], h)))
                }
            }
        }
        return out
    }

    /** Each target has its own 1-second timer; the first one to fill wins. Returns its index or -1. */
    private fun dwellSelect(c: Canvas, now: Long, show: List<RectF>, hit: List<RectF>, hands: List<PointF>): Int {
        var fired = -1; var best = -1; var bestPr = 0f
        for (i in hit.indices) {
            val inside = hands.any { hit[i].contains(it.x, it.y) }
            if (inside) {
                if (cardStart[i] == 0L) cardStart[i] = now
                val pr = ((now - cardStart[i]) / 1000f).coerceIn(0f, 1f)
                val r = show[i]
                c.drawRect(r.left, r.bottom - 14f, r.left + r.width() * pr, r.bottom, prog)
                if (pr > bestPr) { bestPr = pr; best = i }
                if (pr >= 1f && fired < 0) fired = i
            } else cardStart[i] = 0L
        }
        hoverIdx = best
        if (fired >= 0) cardStart.fill(0L)
        return fired
    }

    private fun drawSkeleton(c: Canvas, p: Player, w: Float, h: Float) {
        bone.color = PLAYER_COLORS[p.slot]
        var k = 0
        while (k < bones.size) {
            val a = bones[k]; val d = bones[k + 1]
            if (a < p.n && d < p.n) c.drawLine(p.x(a, w), p.y(a, h), p.x(d, w), p.y(d, h), bone)
            k += 2
        }
        dot.color = PLAYER_COLORS[p.slot]
        for (i in handIdx) if (i < p.n) c.drawCircle(p.x(i, w), p.y(i, h), 18f, dot)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val now = SystemClock.elapsedRealtime()
        val f = latest
        if (f != null && f.ts != seenTs) { seenTs = f.ts; tracker.ingest(f.poses, f.ts) }
        val players = tracker.players(now)
        c.drawRect(0f, 0f, w, h, bg)
        val g = current
        if (g == null) drawMenu(c, w, h, now, players) else drawGame(c, g, w, h, now, players)
        postInvalidateOnAnimation()
    }

    private fun drawMenu(c: Canvas, w: Float, h: Float, now: Long, players: List<Player>) {
        title.textSize = h * 0.08f; c.drawText("POSE PLAY", w * 0.04f, h * 0.11f, title)
        sub.textSize = h * 0.032f
        c.drawText("Raise a hand over a game to start", w * 0.04f, h * 0.17f, sub)

        val n = games.size
        val margin = w * 0.04f; val gap = w * 0.015f
        val cw = (w - 2 * margin - gap * (n - 1)) / n; val ch = h * 0.40f; val top = h * 0.22f
        val rects = games.indices.map { i -> RectF(margin + i * (cw + gap), top, margin + i * (cw + gap) + cw, top + ch) }
        val hitRects = rects.map { RectF(it.left - gap / 2, h * 0.05f, it.right + gap / 2, h * 0.80f) }
        for ((i, g) in games.withIndex()) {
            val r = rects[i]
            fill.color = Color.rgb(28, 38, 50); c.drawRoundRect(r, 24f, 24f, fill)
            fill.color = g.color; c.drawRoundRect(RectF(r.left, r.top, r.right, r.top + ch * 0.28f), 24f, 24f, fill)
            border.strokeWidth = if (i == selected) 8f else 2f
            border.color = if (i == selected) Color.WHITE else Color.rgb(70, 85, 100)
            c.drawRoundRect(r, 24f, 24f, border)
            title.textSize = h * 0.038f
            drawWrapped(c, g.title, r.left + 20f, r.top + ch * 0.18f, cw - 40f, title)
            sub.textSize = h * 0.027f
            drawWrapped(c, g.desc, r.left + 20f, r.top + ch * 0.40f, cw - 40f, sub)
            val best = prefs.getInt("best_${g.key}", 0)
            if (best > 0) { sub.textSize = h * 0.03f; c.drawText("Best: $best", r.left + 20f, r.bottom - 30f, sub) }
        }
        val hands = raisedHands(players, w, h)
        val hit = if (now - menuAt > 1000L) dwellSelect(c, now, rects, hitRects, hands) else -1
        if (hoverIdx >= 0) selected = hoverIdx
        for (p in players) drawSkeleton(c, p, w, h)
        if (hit >= 0) { startGame(hit); return }

        sub.textSize = h * 0.032f
        c.drawText("Players: ${players.size}", w * 0.04f, h * 0.74f, sub)
        for (p in players) { fill.color = PLAYER_COLORS[p.slot]; c.drawCircle(w * 0.04f + 220f + p.slot * 50f, h * 0.74f - 10f, 18f, fill) }
        sub.textSize = h * 0.024f
        val lines = info.split('\n')
        var ty = h * 0.92f
        val shown = if (debug) lines else listOfNotNull(lines.getOrNull(0), lines.getOrNull(2))
        for (l in shown) { c.drawText(l, w * 0.04f, ty, sub); ty += sub.textSize * 1.2f }
        thumb?.let { if (now - thumbAt < 1500L) c.drawBitmap(it, null, RectF(w - 280f, 20f, w - 20f, 20f + 200f), null) }
    }

    private fun drawGame(c: Canvas, g: Game, w: Float, h: Float, now: Long, players: List<Player>) {
        g.tick(now, players, w, h)
        g.draw(c, w, h, players)
        for (p in players) drawSkeleton(c, p, w, h)

        if (g.phase == 1) {
            if (g.roundMs > 0) {
                val left = ((g.roundMs - (now - g.phaseStart)) / 1000L).toInt() + 1
                center.textSize = h * 0.09f; c.drawText(left.toString(), w / 2, h * 0.11f, center)
            }
            if (now - g.phaseStart < 2500L) { center.textSize = h * 0.05f; c.drawText(g.hint, w / 2, h * 0.20f, center) }
        }
        for (s in 0 until 4) if (g.joined[s]) {
            hud.color = PLAYER_COLORS[s]; hud.textSize = h * 0.05f
            c.drawText("${PLAYER_NAMES[s]} ${g.status(s)}", w * (0.03f + 0.25f * s), h - 30f, hud)
        }

        if (g.phase == 0) {
            val num = 3 - ((now - g.phaseStart) / 1000L).toInt()
            if (num != lastCount) { lastCount = num; Sfx.tick() }
            center.textSize = h * 0.3f; c.drawText(num.coerceAtLeast(1).toString(), w / 2, h * 0.55f, center)
            center.textSize = h * 0.06f; c.drawText(g.hint, w / 2, h * 0.22f, center)
        }

        if (g.phase == 2) {
            if (!savedResult) {
                savedResult = true
                val best = (0 until 4).filter { g.joined[it] }.maxOfOrNull { g.scores[it] } ?: 0
                val key = "best_${g.key}"
                newBest = best > prefs.getInt(key, 0)
                if (newBest) prefs.edit().putInt(key, best).apply()
            }
            fill.color = Color.argb(200, 0, 0, 0); c.drawRect(0f, 0f, w, h, fill)
            val ranked = (0 until 4).filter { g.joined[it] }.sortedByDescending { g.scores[it] }
            val head = when {
                ranked.isEmpty() -> "No players"
                ranked.size >= 2 && g.scores[ranked[0]] == g.scores[ranked[1]] -> "Draw!"
                ranked.size >= 2 -> "${PLAYER_NAMES[ranked[0]]} wins!"
                else -> "Game over"
            }
            center.color = Color.WHITE
            center.textSize = h * 0.11f; c.drawText(head, w / 2, h * 0.22f, center)
            if (newBest) { center.textSize = h * 0.05f; c.drawText("NEW BEST!", w / 2, h * 0.30f, center) }
            var y = h * 0.42f
            for ((k, s) in ranked.withIndex()) {
                center.color = PLAYER_COLORS[s]; center.textSize = h * 0.07f
                c.drawText("${k + 1}.  ${PLAYER_NAMES[s]}   ${g.status(s)}", w / 2, y, center)
                y += h * 0.09f
            }
            center.color = Color.WHITE
            val show = listOf(RectF(w * 0.18f, h * 0.76f, w * 0.48f, h * 0.88f), RectF(w * 0.52f, h * 0.76f, w * 0.82f, h * 0.88f))
            val hit = listOf(RectF(w * 0.05f, h * 0.05f, w * 0.5f, h), RectF(w * 0.5f, h * 0.05f, w * 0.95f, h))
            val labels = listOf("Play again", "Menu")
            for ((i, r) in show.withIndex()) {
                fill.color = Color.rgb(40, 55, 72); c.drawRoundRect(r, 20f, 20f, fill)
                center.textSize = h * 0.05f; c.drawText(labels[i], r.centerX(), r.centerY() + h * 0.018f, center)
            }
            val sel = if (now - g.phaseStart > 2000L) dwellSelect(c, now, show, hit, raisedHands(players, w, h)) else -1
            for (p in players) drawSkeleton(c, p, w, h)
            if (sel == 0) startGame(games.indexOf(g)) else if (sel == 1) backToMenu()
        }
    }
}
