package com.razcorp.posetest

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View

/** TV screen: game menu (remote or hand-hover to select), countdown, play, results. Supports 1-4 players. */
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
    private var dwellIdx = -1
    private var dwellStart = 0L
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

    fun backToMenu() { current = null; menuAt = SystemClock.elapsedRealtime(); dwellIdx = -1 }

    private fun startGame(i: Int) {
        val now = SystemClock.elapsedRealtime()
        val g = games[i]
        selected = i; current = g
        g.reset(now, tracker.players(now))
        savedResult = false; newBest = false; lastCount = -1; dwellIdx = -1
    }

    fun onKey(k: Int): Boolean {
        val g = current
        val ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
        if (g == null) {
            return when (k) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { selected = (selected + games.size - 1) % games.size; true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { selected = (selected + 1) % games.size; true }
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

    private fun handsOf(players: List<Player>, w: Float, h: Float): List<PointF> {
        val out = ArrayList<PointF>()
        for (p in players) for (i in handIdx) if (i < p.n) out.add(PointF(p.x(i, w), p.y(i, h)))
        return out
    }

    private fun dwellSelect(c: Canvas, now: Long, rects: List<RectF>, hands: List<PointF>): Int {
        var hit = -1
        for ((i, r) in rects.withIndex()) if (hands.any { r.contains(it.x, it.y) }) { hit = i; break }
        if (hit != dwellIdx) { dwellIdx = hit; dwellStart = now }
        if (hit >= 0) {
            val pr = ((now - dwellStart) / 1000f).coerceIn(0f, 1f)
            val r = rects[hit]
            c.drawRect(r.left, r.bottom - 14f, r.left + r.width() * pr, r.bottom, prog)
            if (pr >= 1f) { dwellIdx = -1; return hit }
        }
        return -1
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
        if (p.n > 0) {
            hud.color = PLAYER_COLORS[p.slot]; hud.textSize = h * 0.035f
            c.drawText(PLAYER_NAMES[p.slot], p.x(0, w) - 20f, p.y(0, h) - 30f, hud)
        }
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
        title.textSize = h * 0.09f; c.drawText("POSE PLAY", w * 0.04f, h * 0.14f, title)
        sub.textSize = h * 0.03f
        c.drawText("Stand 2-3 m from the camera. Hold a hand over a game for 1 second - or use the remote: LEFT / RIGHT + OK", w * 0.04f, h * 0.20f, sub)

        val n = games.size
        val margin = w * 0.04f; val gap = w * 0.015f
        val cw = (w - 2 * margin - gap * (n - 1)) / n; val ch = h * 0.42f; val top = h * 0.27f
        val rects = games.indices.map { i -> RectF(margin + i * (cw + gap), top, margin + i * (cw + gap) + cw, top + ch) }
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
        val hands = handsOf(players, w, h)
        val hit = if (now - menuAt > 1000L) dwellSelect(c, now, rects, hands) else -1
        if (dwellIdx >= 0) selected = dwellIdx
        for (p in players) drawSkeleton(c, p, w, h)
        if (hit >= 0) { startGame(hit); return }

        sub.textSize = h * 0.032f
        c.drawText("Players in view: ${players.size} (up to 4 - each gets a color)", w * 0.04f, h * 0.80f, sub)
        for (p in players) { fill.color = PLAYER_COLORS[p.slot]; c.drawCircle(w * 0.04f + 30f + p.slot * 60f, h * 0.85f, 22f, fill) }
        sub.textSize = h * 0.024f
        var ty = h * 0.91f
        for (l in info.split('\n')) { c.drawText(l, w * 0.04f, ty, sub); ty += sub.textSize * 1.2f }
        thumb?.let { if (now - thumbAt < 1500L) c.drawBitmap(it, null, RectF(w - 280f, 20f, w - 20f, 20f + 200f), null) }
    }

    private fun drawGame(c: Canvas, g: Game, w: Float, h: Float, now: Long, players: List<Player>) {
        g.tick(now, players, w, h)
        g.draw(c, w, h, players)
        for (p in players) drawSkeleton(c, p, w, h)

        sub.textSize = h * 0.022f
        c.drawText(info.split('\n').first(), 20f, h * 0.03f, sub)

        if (g.phase == 1) {
            if (g.roundMs > 0) {
                val left = ((g.roundMs - (now - g.phaseStart)) / 1000L).toInt() + 1
                center.textSize = h * 0.09f; c.drawText(left.toString(), w / 2, h * 0.11f, center)
            }
            if (now - g.phaseStart < 6000L) { center.textSize = h * 0.04f; c.drawText(g.hint, w / 2, h * 0.19f, center) }
        }
        for (s in 0 until 4) if (g.joined[s]) {
            hud.color = PLAYER_COLORS[s]; hud.textSize = h * 0.05f
            c.drawText("${PLAYER_NAMES[s]} ${g.status(s)}", w * (0.03f + 0.25f * s), h - 30f, hud)
        }

        if (g.phase == 0) {
            val num = 3 - ((now - g.phaseStart) / 1000L).toInt()
            if (num != lastCount) { lastCount = num; Sfx.tick() }
            center.textSize = h * 0.3f; c.drawText(num.coerceAtLeast(1).toString(), w / 2, h * 0.55f, center)
            center.textSize = h * 0.05f; c.drawText(g.title + " - get ready!", w / 2, h * 0.25f, center)
            center.textSize = h * 0.035f; c.drawText(g.hint, w / 2, h * 0.68f, center)
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
                ranked.isEmpty() -> "No players detected"
                ranked.size >= 2 && g.scores[ranked[0]] == g.scores[ranked[1]] -> "Draw!"
                ranked.size >= 2 -> "${PLAYER_NAMES[ranked[0]]} wins!"
                else -> "Game over"
            }
            center.textSize = h * 0.11f; c.drawText(head, w / 2, h * 0.22f, center)
            if (newBest) { center.textSize = h * 0.05f; c.drawText("NEW BEST!", w / 2, h * 0.30f, center) }
            var y = h * 0.42f
            for ((k, s) in ranked.withIndex()) {
                center.color = PLAYER_COLORS[s]; center.textSize = h * 0.07f
                c.drawText("${k + 1}.  ${PLAYER_NAMES[s]}   ${g.status(s)}", w / 2, y, center)
                y += h * 0.09f
            }
            center.color = Color.WHITE
            val rects = listOf(RectF(w * 0.22f, h * 0.78f, w * 0.48f, h * 0.90f), RectF(w * 0.52f, h * 0.78f, w * 0.78f, h * 0.90f))
            val labels = listOf("Play again", "Menu")
            for ((i, r) in rects.withIndex()) {
                fill.color = Color.rgb(40, 55, 72); c.drawRoundRect(r, 20f, 20f, fill)
                center.textSize = h * 0.05f; c.drawText(labels[i], r.centerX(), r.centerY() + h * 0.018f, center)
            }
            val sel = if (now - g.phaseStart > 1500L) dwellSelect(c, now, rects, handsOf(players, w, h)) else -1
            for (p in players) drawSkeleton(c, p, w, h)
            if (sel == 0) startGame(games.indexOf(g)) else if (sel == 1) backToMenu()
            sub.textSize = h * 0.026f
            c.drawText("Hold a hand over a button, or press OK to play again / BACK for the menu", w * 0.5f - 560f, h * 0.95f, sub)
        }
    }
}
