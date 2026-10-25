package com.razcorp.posetest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** TV screen: animated menu grid, games with effects, pause menu, settings, results. 1-4 players. */
class GameView(ctx: Context) : View(ctx) {
    private class Frame(val poses: List<FloatArray>, val ts: Long)
    private class Cur(val slot: Int, val pt: PointF)
    @Volatile private var latest: Frame? = null
    private var seenTs = -1L
    @Volatile private var thumb: Bitmap? = null
    @Volatile private var thumbAt = 0L
    @Volatile var info: String = ""
    @Volatile var cameraLabel: String = "Camera2"
    var onToggleCamera: (() -> Unit)? = null

    private val prefs = ctx.getSharedPreferences("posetest", 0)
    private val tracker = Tracker()
    private val games: List<Game> = listOf(
        BubbleGame(), FruitGame(), DodgeGame(), FitnessGame(true), BalloonGame(),
        MemoryGame(), MoleGame(), CatchGame(), QuizGame(), PoseGame())
    private var selected = 0
    private var current: Game? = null
    private var inSettings = false
    private var setSel = 0
    private var menuAt = 0L
    private val cardStart = LongArray(12)
    private var hoverIdx = -1
    private var savedResult = false
    private var newBest = false
    private var lastCount = -1
    private var lastFrame = 0L
    private var paused = false
    private var pausedRealAt = 0L
    private var pauseSel = 0
    private var pauseHoverStart = 0L
    private var confettiDone = false
    private val curS = arrayOfNulls<PointF>(4)

    private val MENU_TOP = Color.rgb(10, 14, 36)
    private val MENU_BOT = Color.rgb(52, 20, 86)
    private var bgBmp: Bitmap? = null
    private var bgKey = 0
    private val bgPaint = Paint().apply { isFilterBitmap = true }
    private val stars = FloatArray(60 * 4).also { a -> val rnd = Random(11); for (i in 0 until 60) { a[i * 4] = rnd.nextFloat(); a[i * 4 + 1] = rnd.nextFloat(); a[i * 4 + 2] = 0.000004f + 0.00002f * rnd.nextFloat(); a[i * 4 + 3] = rnd.nextFloat() } }
    private var titleShader: Shader? = null
    private var titleShaderW = 0f
    private val path = Path()

    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bone = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(8f, 3f, 3f, Color.BLACK) }
    private val left = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD }
    private val right = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD }
    private val bones = intArrayOf(11,12, 11,13, 13,15, 12,14, 14,16, 11,23, 12,24, 23,24, 23,25, 25,27, 24,26, 26,28)
    private val handIdx = intArrayOf(15, 16, 19, 20)

    fun setFrame(poses: List<FloatArray>) { latest = Frame(poses, SystemClock.elapsedRealtime()) }
    fun setThumb(b: Bitmap) { thumb = b; thumbAt = SystemClock.elapsedRealtime() }
    val inMenu: Boolean get() = current == null
    fun inGame(): Boolean = current != null

    init { Settings.load(prefs) }

    private fun ctext(c: Canvas, t: String, x: Float, y: Float, size: Float, col: Int = Color.WHITE, a: Int = 255) {
        center.textSize = size; center.color = withAlpha(col, a); c.drawText(t, x, y, center)
    }
    private fun fitText(c: Canvas, t: String, x: Float, y: Float, size: Float, maxW: Float, col: Int = Color.WHITE) {
        var s = size; center.textSize = s
        while (center.measureText(t) > maxW && s > size * 0.5f) { s *= 0.93f; center.textSize = s }
        center.color = col; c.drawText(t, x, y, center)
    }
    private fun ltext(c: Canvas, t: String, x: Float, y: Float, size: Float, col: Int = Color.WHITE) {
        left.textSize = size; left.color = col; c.drawText(t, x, y, left)
    }
    private fun rtext(c: Canvas, t: String, x: Float, y: Float, size: Float, col: Int = Color.WHITE) {
        right.textSize = size; right.color = col; c.drawText(t, x, y, right)
    }

    // ---------- state ----------
    fun backToMenu() {
        current = null; inSettings = false; menuAt = SystemClock.elapsedRealtime(); cardStart.fill(0L)
        paused = false; GameClock.resume(); Fx.clear()
    }

    private fun startGame(i: Int) {
        paused = false; GameClock.resume(); Fx.clear()
        val now = GameClock.now()
        val g = games[i]
        selected = i; current = g
        g.reset(now, tracker.players(SystemClock.elapsedRealtime()))
        savedResult = false; newBest = false; lastCount = -1; confettiDone = false; cardStart.fill(0L); pauseHoverStart = 0L
    }

    private fun openSettings() { inSettings = true; setSel = 0; menuAt = SystemClock.elapsedRealtime(); cardStart.fill(0L) }

    fun togglePause() {
        val g = current ?: return
        if (g.phase == 2) return
        paused = !paused
        if (paused) { GameClock.pause(); pausedRealAt = SystemClock.elapsedRealtime(); pauseSel = 0; cardStart.fill(0L) }
        else { GameClock.resume(); cardStart.fill(0L); pauseHoverStart = 0L }
    }

    fun onBack(): Boolean {
        if (inSettings) { inSettings = false; menuAt = SystemClock.elapsedRealtime(); cardStart.fill(0L); return true }
        val g = current ?: return false
        if (g.phase == 2) backToMenu() else togglePause()
        return true
    }

    private fun pauseAction(i: Int) {
        val g = current ?: return
        when (i) { 0 -> togglePause(); 1 -> startGame(games.indexOf(g)); else -> backToMenu() }
    }

    private fun settingsAction(i: Int) {
        when (i) {
            0 -> Settings.lite = !Settings.lite
            1 -> Settings.sound = !Settings.sound
            2 -> onToggleCamera?.invoke()
            3 -> Settings.debug = !Settings.debug
            else -> { inSettings = false; menuAt = SystemClock.elapsedRealtime() }
        }
        Settings.save(prefs)
        cardStart.fill(0L)
    }

    fun onKey(k: Int): Boolean {
        val g = current
        val ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
        val total = games.size + 1
        if (g == null) {
            if (inSettings) {
                when (k) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { setSel = (setSel + 4) % 5; return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { setSel = (setSel + 1) % 5; return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { settingsAction(setSel); return true }
                }
                return false
            }
            return when (k) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { selected = (selected + total - 1) % total; true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { selected = (selected + 1) % total; true }
                KeyEvent.KEYCODE_DPAD_UP -> { if (selected >= 6) selected -= 6; true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { if (selected + 6 < total) selected += 6; true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (selected < games.size) startGame(selected) else openSettings(); true }
                else -> false
            }
        }
        if (paused) {
            when (k) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { pauseSel = (pauseSel + 2) % 3; return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { pauseSel = (pauseSel + 1) % 3; return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { pauseAction(pauseSel); return true }
            }
            return false
        }
        if (g.phase == 2 && ok) { startGame(games.indexOf(g)); return true }
        if (g.phase < 2 && ok) { togglePause(); return true }
        return false
    }

    // ---------- helpers ----------
    private fun drawWrapped(c: Canvas, text: String, x: Float, y: Float, maxW: Float, p: Paint): Float {
        var line = ""; var yy = y
        for (word in text.split(" ")) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(t) > maxW && line.isNotEmpty()) { c.drawText(line, x, yy, p); yy += p.textSize * 1.25f; line = word } else line = t
        }
        if (line.isNotEmpty()) { c.drawText(line, x, yy, p); yy += p.textSize * 1.25f }
        return yy
    }

    /** Body-relative cursors (one per player with a raised hand), smoothed. */
    private fun cursorsFor(players: List<Player>, w: Float, h: Float): List<Cur> {
        val full = RectF(0f, 0f, w, h)
        val out = ArrayList<Cur>()
        for (p in players) {
            val t = cursorOf(p, full)
            if (t == null) { curS[p.slot] = null; continue }
            val o = curS[p.slot]
            val n = if (o == null) PointF(t.x, t.y) else PointF(o.x + (t.x - o.x) * 0.5f, o.y + (t.y - o.y) * 0.5f)
            curS[p.slot] = n; out.add(Cur(p.slot, n))
        }
        return out
    }

    private fun drawCursors(c: Canvas, curs: List<Cur>, h: Float) {
        for (cu in curs) {
            val col = PLAYER_COLORS[cu.slot]
            dot.color = withAlpha(col, 90); c.drawCircle(cu.pt.x, cu.pt.y, h * 0.04f, dot)
            border.color = lighten(col, 0.3f); border.strokeWidth = 5f; c.drawCircle(cu.pt.x, cu.pt.y, h * 0.03f, border)
            dot.color = Color.WHITE; c.drawCircle(cu.pt.x, cu.pt.y, h * 0.01f, dot)
        }
    }

    private fun allHands(players: List<Player>, w: Float, h: Float): List<PointF> {
        val out = ArrayList<PointF>()
        for (p in players) for (i in handIdx) if (i < p.n) out.add(PointF(p.x(i, w), p.y(i, h)))
        return out
    }

    private fun dwellSelect(c: Canvas, now: Long, show: List<RectF>, hit: List<RectF>, pts: List<PointF>): Int {
        var fired = -1; var best = -1; var bestPr = 0f
        for (i in hit.indices) {
            val inside = pts.any { hit[i].contains(it.x, it.y) }
            if (inside) {
                if (cardStart[i] == 0L) cardStart[i] = now
                val pr = ((now - cardStart[i]) / 1000f).coerceIn(0f, 1f)
                val r = show[i]
                fill.color = withAlpha(Color.WHITE, 230)
                c.drawRoundRect(RectF(r.left + 14f, r.bottom - 18f, r.left + 14f + (r.width() - 28f) * pr, r.bottom - 8f), 5f, 5f, fill)
                if (pr > bestPr) { bestPr = pr; best = i }
                if (pr >= 1f && fired < 0) fired = i
            } else cardStart[i] = 0L
        }
        hoverIdx = best
        if (fired >= 0) cardStart.fill(0L)
        return fired
    }

    private fun ensureBg(w: Float, h: Float, top: Int, bot: Int) {
        val key = (top * 31 + bot) * 31 + w.toInt() * 7 + h.toInt()
        if (key == bgKey && bgBmp != null) return
        bgKey = key
        val bw = max(2, (w / 3).toInt()); val bh = max(2, (h / 3).toInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, bh.toFloat(), top, bot, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        p.shader = RadialGradient(bw * 0.5f, bh * 0.4f, bh * 0.95f, withAlpha(lighten(bot, 0.4f), 90), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        bgBmp?.recycle(); bgBmp = bmp
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float, g: Game?, now: Long) {
        ensureBg(w, h, g?.bgTop ?: MENU_TOP, g?.bgBottom ?: MENU_BOT)
        c.drawBitmap(bgBmp!!, null, RectF(0f, 0f, w, h), bgPaint)
        if (Settings.lite) return
        for (i in 0 until 60) {
            val sx = stars[i * 4] * w
            val sy = ((stars[i * 4 + 1] + now * stars[i * 4 + 2]) % 1f) * h
            val ph = stars[i * 4 + 3]
            dot.color = withAlpha(Color.WHITE, (110 + 100 * sin(now / 700f + ph * 6f)).toInt())
            c.drawCircle(sx, sy, 1.5f + ph * 3f, dot)
        }
    }

    private fun drawSkeleton(c: Canvas, p: Player, w: Float, h: Float) {
        val col = PLAYER_COLORS[p.slot]
        if (p.n > 24) {
            path.reset()
            path.moveTo(p.x(11, w), p.y(11, h)); path.lineTo(p.x(12, w), p.y(12, h)); path.lineTo(p.x(24, w), p.y(24, h)); path.lineTo(p.x(23, w), p.y(23, h)); path.close()
            fill.color = withAlpha(col, 70); c.drawPath(path, fill)
        }
        for (pass in 0 until 2) {
            if (pass == 0 && Settings.lite) continue
            bone.strokeWidth = if (pass == 0) 20f else 7f
            bone.color = if (pass == 0) withAlpha(col, 70) else lighten(col, 0.35f)
            var k = 0
            while (k < bones.size) {
                val a = bones[k]; val d = bones[k + 1]
                if (a < p.n && d < p.n) c.drawLine(p.x(a, w), p.y(a, h), p.x(d, w), p.y(d, h), bone)
                k += 2
            }
        }
        if (p.n > 12) {
            val sw = Math.hypot((p.x(11, w) - p.x(12, w)).toDouble(), (p.y(11, h) - p.y(12, h)).toDouble()).toFloat()
            val hr = (sw * 0.42f).coerceIn(h * 0.03f, h * 0.09f)
            val hx = p.x(0, w); val hy = p.y(0, h)
            fill.color = withAlpha(col, 110); c.drawCircle(hx, hy, hr, fill)
            border.color = lighten(col, 0.4f); border.strokeWidth = 5f; c.drawCircle(hx, hy, hr, border)
            if (p.n > 5) for (e in intArrayOf(2, 5)) {
                fill.color = Color.WHITE; c.drawCircle(p.x(e, w), p.y(e, h), hr * 0.17f, fill)
                fill.color = Color.rgb(20, 24, 40); c.drawCircle(p.x(e, w), p.y(e, h), hr * 0.08f, fill)
            }
            val tr = RectF(hx - hr * 0.9f, hy - hr - h * 0.05f, hx + hr * 0.9f, hy - hr - h * 0.005f)
            fill.color = withAlpha(col, 220); c.drawRoundRect(tr, 14f, 14f, fill)
            ctext(c, PLAYER_NAMES[p.slot], tr.centerX(), tr.centerY() + h * 0.014f, h * 0.034f, Color.rgb(12, 16, 28))
        }
        for (i in intArrayOf(13, 14, 25, 26, 27, 28)) if (i < p.n) { dot.color = Color.WHITE; c.drawCircle(p.x(i, w), p.y(i, h), 7f, dot) }
        for (i in handIdx) if (i < p.n) {
            dot.color = withAlpha(col, 100); c.drawCircle(p.x(i, w), p.y(i, h), 28f, dot)
            dot.color = lighten(col, 0.5f); c.drawCircle(p.x(i, w), p.y(i, h), 14f, dot)
        }
    }

    // ---------- main draw ----------
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val realNow = SystemClock.elapsedRealtime()
        val dt = if (lastFrame == 0L) 16L else min(50L, realNow - lastFrame)
        lastFrame = realNow
        val f = latest
        if (f != null && f.ts != seenTs) { seenTs = f.ts; tracker.ingest(f.poses, f.ts) }
        val players = tracker.players(realNow)
        val g = current
        drawBackground(c, w, h, g, realNow)
        if (g == null) { if (inSettings) drawSettings(c, w, h, realNow, players) else drawMenu(c, w, h, realNow, players) }
        else drawGame(c, g, w, h, realNow, dt, players)
        postInvalidateOnAnimation()
    }

    // ---------- menu ----------
    private fun gearIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        ringP.color = Color.rgb(200, 210, 230); ringP.strokeWidth = s * 0.28f
        c.drawCircle(cx, cy, s * 0.5f, ringP)
        for (k in 0 until 8) {
            val a = k * 0.7854f
            ringP.strokeWidth = s * 0.22f
            c.drawLine(cx + kotlin.math.cos(a) * s * 0.62f, cy + kotlin.math.sin(a) * s * 0.62f, cx + kotlin.math.cos(a) * s * 0.9f, cy + kotlin.math.sin(a) * s * 0.9f, ringP)
        }
    }

    private fun drawMenu(c: Canvas, w: Float, h: Float, now: Long, players: List<Player>) {
        if (titleShader == null || titleShaderW != w) {
            titleShader = LinearGradient(w * 0.04f, 0f, w * 0.42f, 0f, Color.rgb(80, 220, 255), Color.rgb(255, 90, 200), Shader.TileMode.CLAMP); titleShaderW = w
        }
        title.shader = titleShader; title.textSize = h * 0.1f
        c.drawText("POSE PLAY", w * 0.04f, h * 0.125f, title)
        title.shader = null
        ltext(c, "by RAZ Corp  -  We Build to Play", w * 0.042f, h * 0.175f, h * 0.028f, Color.rgb(160, 175, 205))
        rtext(c, "Raise a hand to point - hold over a game to start", w * 0.96f, h * 0.12f, h * 0.03f, Color.rgb(210, 220, 240))
        for (p in players) {
            val bx = w * 0.96f - 30f - (3 - p.slot) * 70f
            fill.color = PLAYER_COLORS[p.slot]; c.drawCircle(bx, h * 0.165f, 24f, fill)
            ctext(c, PLAYER_NAMES[p.slot], bx, h * 0.165f + 9f, h * 0.026f, Color.rgb(12, 16, 28))
        }

        val total = games.size + 1
        val cols = 6
        val margin = w * 0.04f; val gap = w * 0.012f
        val cw = (w - 2 * margin - gap * (cols - 1)) / cols; val ch = h * 0.215f
        val top = h * 0.2f; val rowGap = h * 0.02f
        val rects = (0 until total).map { i ->
            val r = i / cols; val cc = i % cols
            RectF(margin + cc * (cw + gap), top + r * (ch + rowGap), margin + cc * (cw + gap) + cw, top + r * (ch + rowGap) + ch)
        }
        val hitRects = rects.map { RectF(it.left - 6f, it.top - 6f, it.right + 6f, it.bottom + 6f) }
        val pulse = 0.5f + 0.5f * sin(now / 300f)
        for (i in 0 until total) {
            val r = rects[i]; val sel = i == selected
            val g = if (i < games.size) games[i] else null
            val col = g?.color ?: Color.rgb(110, 125, 150)
            c.save()
            if (sel) c.scale(1.06f, 1.06f, r.centerX(), r.centerY())
            if (!Settings.lite) {
                fill.shader = null
                fill.color = withAlpha(col, if (sel) (70 + 70 * pulse).toInt() else 24)
                c.drawRoundRect(RectF(r.left - 10f, r.top - 10f, r.right + 10f, r.bottom + 10f), 34f, 34f, fill)
            }
            fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, darken(col, 0.5f), Color.rgb(16, 22, 36), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 26f, 26f, fill)
            fill.shader = null
            border.strokeWidth = if (sel) 7f else 2f
            border.color = if (sel) Color.WHITE else withAlpha(col, 170)
            c.drawRoundRect(r, 26f, 26f, border)
            if (g != null) g.drawIcon(c, r.centerX(), r.top + ch * 0.36f, ch * 0.2f) else gearIcon(c, r.centerX(), r.top + ch * 0.36f, ch * 0.2f)
            fitText(c, g?.title ?: "Settings", r.centerX(), r.top + ch * 0.76f, h * 0.03f, cw - 20f)
            if (g != null) {
                val best = prefs.getInt("best_${g.key}", 0)
                if (best > 0) ctext(c, "BEST ${g.fmtBest(best)}", r.centerX(), r.top + ch * 0.93f, h * 0.02f, Color.rgb(255, 215, 90))
            }
            c.restore()
        }

        // info panel for the highlighted item
        val panel = RectF(w * 0.04f, h * 0.69f, w * 0.96f, h * 0.84f)
        fill.color = withAlpha(Color.rgb(6, 10, 24), 150); c.drawRoundRect(panel, 26f, 26f, fill)
        val sg = if (selected < games.size) games[selected] else null
        ltext(c, sg?.title ?: "Settings", panel.left + 30f, panel.top + h * 0.055f, h * 0.045f, Color.WHITE)
        left.textSize = h * 0.028f; left.color = Color.rgb(190, 200, 220)
        drawWrapped(c, sg?.desc ?: "Lite graphics, sound, TV camera mode and debug info.", panel.left + 30f, panel.top + h * 0.095f, panel.width() * 0.62f, left)
        if (sg != null) {
            rtext(c, sg.tag, panel.right - 30f, panel.top + h * 0.055f, h * 0.03f, Color.rgb(150, 200, 255))
            val best = prefs.getInt("best_${sg.key}", 0)
            if (best > 0) rtext(c, "Best: ${sg.fmtBest(best)}", panel.right - 30f, panel.top + h * 0.1f, h * 0.034f, Color.rgb(255, 215, 90))
        }

        val curs = cursorsFor(players, w, h)
        val hit = if (now - menuAt > 1000L) dwellSelect(c, now, rects, hitRects, curs.map { it.pt }) else -1
        if (hoverIdx >= 0) selected = hoverIdx
        for (p in players) drawSkeleton(c, p, w, h)
        drawCursors(c, curs, h)
        if (hit >= 0) { if (hit < games.size) startGame(hit) else openSettings(); return }

        val lines = info.split('\n')
        val shown = if (Settings.debug) lines else listOfNotNull(lines.getOrNull(0), lines.getOrNull(2))
        var ty = h * 0.895f
        for (l in shown) { ltext(c, l, w * 0.04f, ty, h * 0.022f, Color.rgb(140, 155, 185)); ty += h * 0.028f }
        thumb?.let {
            if (now - thumbAt < 1500L) {
                val tr = RectF(w - 260f, h - 200f, w - 20f, h - 20f)
                c.drawBitmap(it, null, tr, null); border.color = Color.WHITE; border.strokeWidth = 3f; c.drawRect(tr, border)
            }
        }
    }

    private fun drawSettings(c: Canvas, w: Float, h: Float, now: Long, players: List<Player>) {
        ctext(c, "SETTINGS", w / 2, h * 0.16f, h * 0.09f)
        val labels = listOf("Lite graphics", "Sound", "TV camera", "Debug info", "Back")
        val values = listOf(if (Settings.lite) "ON" else "OFF", if (Settings.sound) "ON" else "OFF", cameraLabel, if (Settings.debug) "ON" else "OFF", "")
        val hints = listOf("Faster on slow TVs", "Beeps and effects", "Camera2 or UVC", "Show technical info", "")
        val cols = intArrayOf(Color.rgb(90, 140, 230), Color.rgb(230, 150, 60), Color.rgb(70, 190, 130), Color.rgb(170, 100, 220), Color.rgb(110, 120, 150))
        val cw = w * 0.17f; val gap = w * 0.015f; val x0 = (w - (5 * cw + 4 * gap)) / 2
        val show = labels.indices.map { RectF(x0 + it * (cw + gap), h * 0.34f, x0 + it * (cw + gap) + cw, h * 0.6f) }
        val hit = show.map { RectF(it.left - 6f, it.top - 6f, it.right + 6f, it.bottom + 6f) }
        for ((i, r) in show.withIndex()) {
            fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, darken(cols[i], 0.2f), darken(cols[i], 0.7f), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 28f, 28f, fill); fill.shader = null
            border.color = if (i == setSel) Color.WHITE else withAlpha(cols[i], 160); border.strokeWidth = if (i == setSel) 8f else 2f
            c.drawRoundRect(r, 28f, 28f, border)
            fitText(c, labels[i], r.centerX(), r.top + r.height() * 0.3f, h * 0.036f, cw - 24f)
            if (values[i].isNotEmpty()) ctext(c, values[i], r.centerX(), r.top + r.height() * 0.62f, h * 0.06f, if (values[i] == "OFF") Color.rgb(200, 120, 120) else Color.rgb(140, 240, 160))
            if (hints[i].isNotEmpty()) fitText(c, hints[i], r.centerX(), r.top + r.height() * 0.86f, h * 0.022f, cw - 24f, Color.rgb(190, 200, 220))
        }
        val curs = cursorsFor(players, w, h)
        val sel = if (SystemClock.elapsedRealtime() - menuAt > 800L) dwellSelect(c, now, show, hit, curs.map { it.pt }) else -1
        if (hoverIdx >= 0) setSel = hoverIdx
        for (p in players) drawSkeleton(c, p, w, h)
        drawCursors(c, curs, h)
        if (sel >= 0) settingsAction(sel)
    }

    // ---------- game ----------
    private fun drawPauseButton(c: Canvas, w: Float, h: Float, now: Long, hands: List<PointF>): Boolean {
        val s = h * 0.1f
        val r = RectF(w - s - 24f, 24f, w - 24f, 24f + s)
        val hit = RectF(w - s * 2.2f, 0f, w, s * 2.2f)
        fill.color = withAlpha(Color.BLACK, 120); c.drawRoundRect(r, s * 0.3f, s * 0.3f, fill)
        fill.color = Color.WHITE
        c.drawRoundRect(RectF(r.left + s * 0.3f, r.top + s * 0.25f, r.left + s * 0.45f, r.bottom - s * 0.25f), 4f, 4f, fill)
        c.drawRoundRect(RectF(r.right - s * 0.45f, r.top + s * 0.25f, r.right - s * 0.3f, r.bottom - s * 0.25f), 4f, 4f, fill)
        if (hands.any { hit.contains(it.x, it.y) }) {
            if (pauseHoverStart == 0L) pauseHoverStart = now
            val pr = ((now - pauseHoverStart) / 1200f).coerceIn(0f, 1f)
            ringP.color = Color.WHITE; ringP.strokeWidth = 9f
            c.drawArc(RectF(r.left - 10f, r.top - 10f, r.right + 10f, r.bottom + 10f), -90f, 360f * pr, false, ringP)
            if (pr >= 1f) { pauseHoverStart = 0L; return true }
        } else pauseHoverStart = 0L
        return false
    }

    private fun drawGame(c: Canvas, g: Game, w: Float, h: Float, realNow: Long, dt: Long, players: List<Player>) {
        val now = GameClock.now()
        if (!paused) { g.tick(now, players, w, h); Fx.update(dt) }

        c.save()
        Fx.applyShake(c)
        g.draw(c, w, h, players)
        for (p in players) drawSkeleton(c, p, w, h)
        Fx.draw(c, h)
        c.restore()

        if (g.phase < 2) {
            for (s in 0 until 4) if (g.joined[s]) {
                val r = pillRect(s, w, h); val rad = r.height() / 2
                fill.color = withAlpha(Color.rgb(8, 12, 24), 170); c.drawRoundRect(r, rad, rad, fill)
                border.color = PLAYER_COLORS[s]; border.strokeWidth = 4f; c.drawRoundRect(r, rad, rad, border)
                fill.color = PLAYER_COLORS[s]; c.drawCircle(r.left + rad, r.centerY(), rad * 0.72f, fill)
                ctext(c, (s + 1).toString(), r.left + rad, r.centerY() + r.height() * 0.15f, r.height() * 0.42f, Color.rgb(12, 16, 28))
                ltext(c, g.status(s), r.left + r.height() * 0.95f, r.centerY() + r.height() * 0.18f, r.height() * 0.42f)
            }
            g.drawOverlay(c, w, h)
        }
        if (g.phase == 1 && g.roundMs > 0) {
            val leftMs = max(0L, g.roundMs - (now - g.phaseStart))
            val cx = w / 2; val cy = h * 0.1f; val rr = h * 0.065f
            fill.color = withAlpha(Color.BLACK, 130); c.drawCircle(cx, cy, rr, fill)
            ringP.strokeWidth = 10f; ringP.color = if (leftMs < 10000L) Color.rgb(255, 80, 80) else Color.WHITE
            c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), -90f, 360f * leftMs / g.roundMs.toFloat(), false, ringP)
            val secs = ((leftMs + 999L) / 1000L).toInt()
            ctext(c, if (secs >= 100) "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}" else secs.toString(), cx, cy + rr * 0.3f, rr * (if (secs >= 100) 0.7f else 0.9f))
        }
        if (g.phase == 1) {
            val t = now - g.phaseStart
            if (t < 700L) ctext(c, "GO!", w / 2, h * 0.5f, h * 0.22f * (1f + 0.4f * (1f - t / 700f)), Color.rgb(255, 235, 120), (255 * (1f - t / 700f)).toInt())
            else if (t < 3000L && g.roundMs >= 0L && g.key != "quiz" && g.key != "pose" && g.key != "puzzle" && g.key != "memory")
                ctext(c, g.hint, w / 2, h * 0.22f, h * 0.05f, Color.WHITE, (255 * (1f - (t - 2200L).coerceIn(0L, 800L) / 800f)).toInt())
        }

        if (g.phase == 0) {
            val el = now - g.phaseStart
            val num = 3 - (el / 1000L).toInt()
            val f = (el % 1000L) / 1000f
            if (num != lastCount) { lastCount = num; Sfx.tick() }
            ctext(c, g.title, w / 2, h * 0.2f, h * 0.07f, lighten(g.color, 0.4f))
            ctext(c, g.hint, w / 2, h * 0.27f, h * 0.04f, Color.rgb(210, 220, 240))
            ctext(c, num.coerceAtLeast(1).toString(), w / 2, h * 0.62f, h * 0.34f * (1.5f - 0.5f * min(1f, f * 4f)), Color.WHITE, (255 * (1f - 0.6f * f)).toInt())
        }

        val canPause = g.phase == 1 && !paused && now - g.phaseStart > 1500L
        if (g.phase < 2 && !paused) {
            if (drawPauseButton(c, w, h, realNow, if (canPause) allHands(players, w, h) else emptyList())) togglePause()
        }

        if (g.phase == 2) drawResults(c, g, w, h, realNow, players)
        if (paused) drawPause(c, w, h, realNow, players)
    }

    private fun drawResults(c: Canvas, g: Game, w: Float, h: Float, now: Long, players: List<Player>) {
        val ranked = (0 until 4).filter { g.joined[it] }.sortedByDescending { g.scores[it] }
        if (!savedResult) {
            savedResult = true
            val best = g.bestValue()
            val key = "best_${g.key}"
            newBest = best > prefs.getInt(key, 0)
            if (newBest) prefs.edit().putInt(key, best).apply()
        }
        if (!confettiDone) { confettiDone = true; if (ranked.isNotEmpty() && g.scores[ranked[0]] > 0) Fx.confetti(w, h) }
        fill.color = withAlpha(Color.rgb(4, 6, 14), 215); c.drawRect(0f, 0f, w, h, fill)
        val head = g.resultHeadline(ranked) ?: when {
            ranked.isEmpty() -> "No players"
            ranked.size >= 2 && g.scores[ranked[0]] == g.scores[ranked[1]] -> "Draw!"
            ranked.size >= 2 -> "${PLAYER_NAMES[ranked[0]]} wins!"
            else -> "Game over"
        }
        val hc = if (g.resultHeadline(ranked) == null && ranked.size >= 2 && g.scores[ranked[0]] != g.scores[ranked[1]]) PLAYER_COLORS[ranked[0]] else Color.WHITE
        ctext(c, head, w / 2, h * 0.16f, h * 0.1f * (1f + 0.04f * sin(now / 250f)), hc)
        if (newBest) ctext(c, "NEW BEST!", w / 2, h * 0.23f, h * 0.045f, Color.rgb(255, 215, 90))
        val medals = intArrayOf(Color.rgb(255, 205, 60), Color.rgb(200, 205, 215), Color.rgb(205, 130, 70), Color.rgb(100, 110, 130))
        val rowH = h * 0.07f
        for ((k, s) in ranked.withIndex()) {
            val y0 = h * 0.28f + k * (rowH + h * 0.015f)
            val r = RectF(w * 0.3f, y0, w * 0.7f, y0 + rowH)
            fill.color = withAlpha(PLAYER_COLORS[s], 55); c.drawRoundRect(r, rowH / 2, rowH / 2, fill)
            border.color = PLAYER_COLORS[s]; border.strokeWidth = if (k == 0) 6f else 3f; c.drawRoundRect(r, rowH / 2, rowH / 2, border)
            fill.color = medals[min(k, 3)]; c.drawCircle(r.left + rowH / 2, r.centerY(), rowH * 0.36f, fill)
            ctext(c, (k + 1).toString(), r.left + rowH / 2, r.centerY() + rowH * 0.15f, rowH * 0.45f, Color.rgb(12, 16, 28))
            ltext(c, PLAYER_NAMES[s], r.left + rowH * 1.1f, r.centerY() + rowH * 0.17f, rowH * 0.5f, PLAYER_COLORS[s])
            rtext(c, g.status(s), r.right - rowH * 0.5f, r.centerY() + rowH * 0.17f, rowH * 0.5f)
        }
        val show = listOf(RectF(w * 0.18f, h * 0.7f, w * 0.48f, h * 0.84f), RectF(w * 0.52f, h * 0.7f, w * 0.82f, h * 0.84f))
        val hit = show.map { RectF(it.left - 6f, it.top - 6f, it.right + 6f, it.bottom + 6f) }
        val labels = listOf("Play again", "Main menu")
        for ((i, r) in show.withIndex()) {
            fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, if (i == 0) Color.rgb(40, 140, 90) else Color.rgb(60, 80, 140), Color.rgb(20, 30, 50), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 28f, 28f, fill); fill.shader = null
            ctext(c, labels[i], r.centerX(), r.centerY() + h * 0.018f, h * 0.05f)
        }
        val curs = cursorsFor(players, w, h)
        val sel = if (now - pausedOrEnd(g) > 2000L) dwellSelect(c, now, show, hit, curs.map { it.pt }) else -1
        Fx.draw(c, h)
        drawCursors(c, curs, h)
        ctext(c, "Raise a hand and point at a button", w / 2, h * 0.92f, h * 0.026f, Color.rgb(150, 165, 195))
        if (sel == 0) startGame(games.indexOf(g)) else if (sel == 1) backToMenu()
    }

    private fun pausedOrEnd(g: Game): Long = SystemClock.elapsedRealtime() - (GameClock.now() - g.phaseStart)

    private fun drawPause(c: Canvas, w: Float, h: Float, now: Long, players: List<Player>) {
        fill.color = withAlpha(Color.rgb(4, 6, 14), 205); c.drawRect(0f, 0f, w, h, fill)
        ctext(c, "PAUSED", w / 2, h * 0.2f, h * 0.12f)
        val labels = listOf("Resume", "Restart", "Main menu")
        val cols = intArrayOf(Color.rgb(50, 160, 100), Color.rgb(200, 150, 50), Color.rgb(80, 100, 180))
        val cw = w * 0.24f; val gap = w * 0.03f; val x0 = (w - (3 * cw + 2 * gap)) / 2
        val show = labels.indices.map { RectF(x0 + it * (cw + gap), h * 0.38f, x0 + it * (cw + gap) + cw, h * 0.6f) }
        val hit = show.map { RectF(it.left - 6f, it.top - 6f, it.right + 6f, it.bottom + 6f) }
        for ((i, r) in show.withIndex()) {
            fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, darken(cols[i], 0.2f), darken(cols[i], 0.7f), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 30f, 30f, fill); fill.shader = null
            border.color = if (i == pauseSel) Color.WHITE else withAlpha(cols[i], 160)
            border.strokeWidth = if (i == pauseSel) 8f else 2f
            c.drawRoundRect(r, 30f, 30f, border)
            ctext(c, labels[i], r.centerX(), r.centerY() + h * 0.017f, h * 0.055f)
        }
        ctext(c, "Raise a hand and point at a button  -  or use the remote", w / 2, h * 0.8f, h * 0.03f, Color.rgb(160, 175, 205))
        val curs = cursorsFor(players, w, h)
        val sel = if (now - pausedRealAt > 800L) dwellSelect(c, now, show, hit, curs.map { it.pt }) else -1
        if (hoverIdx >= 0) pauseSel = hoverIdx
        for (p in players) drawSkeleton(c, p, w, h)
        drawCursors(c, curs, h)
        if (sel >= 0) pauseAction(sel)
    }
}
