package com.razcorp.posetest

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {
    private val isTv by lazy {
        (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
    private var overlay: OverlayView? = null   // phone preview
    private var game: GameView? = null         // TV game
    private var barView: LinearLayout? = null
    private var pose: PoseRunner? = null
    private var source: FrameSource? = null
    private var useUvc = false
    private var facing = CameraCharacteristics.LENS_FACING_BACK
    private var server: TvServer? = null
    private var link: PhoneLink? = null
    private var nsd: NsdFinder? = null
    private val camFrames = AtomicInteger()
    @Volatile private var camStatus = ""
    @Volatile private var netStatus = ""
    @Volatile private var remoteTs = 0L
    private var crashInfo = ""
    private val ui = Handler(Looper.getMainLooper())
    private var lastCam = 0
    private var lastPose = 0
    private var started = false
    private var restartPending = false
    private var lastTouch = 0L
    private var rotMode = 0
    private val prefs by lazy { getSharedPreferences("posetest", 0) }

    private fun remoteActive() = SystemClock.elapsedRealtime() - remoteTs < 1500
    private fun hasCam() = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun installCrashLogger() {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val where = e.stackTrace.firstOrNull { it.className.startsWith("com.razcorp") } ?: e.stackTrace.firstOrNull()
                prefs.edit().putString("crash", "${e.javaClass.simpleName}: ${e.message} @ $where".take(300)).commit()
            } catch (_: Throwable) {}
            old?.uncaughtException(t, e)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        installCrashLogger()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = if (isTv) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        prefs.getString("crash", null)?.let { crashInfo = "Last crash: $it"; prefs.edit().remove("crash").apply() }
        if (isTv) { game = GameView(this); game?.onToggleCamera = { useUvc = !useUvc; startSource() }; setContentView(game) } else buildPhoneUi()
        if (!hasCam()) requestPermissions(arrayOf(Manifest.permission.CAMERA), 1) else begin()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        if (r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED) begin()
        else camStatus = "Camera permission is needed - allow it in app settings"
    }

    override fun onResume() {
        super.onResume()
        if (!started && hasCam()) begin()
    }

    // ---------- phone UI ----------
    private fun touched() { lastTouch = SystemClock.elapsedRealtime(); barView?.visibility = View.VISIBLE }

    private fun buildPhoneUi() {
        val root = FrameLayout(this)
        overlay = OverlayView(this); root.addView(overlay)
        overlay!!.setOnClickListener { touched() }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0x99000000.toInt()) }
        fun btn(t: String, f: () -> Unit) {
            val bt = Button(this); bt.text = t; bt.textSize = 12f; bt.isAllCaps = false
            bt.setOnClickListener { touched(); f() }
            bar.addView(bt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        btn("Wi-Fi") { connectWifiAuto() }
        btn("TV IP") { askIp() }
        btn("Bluetooth") { pickBt() }
        btn("Flip") { flipCamera() }
        btn("Pause") { link?.sendCmd(1) }
        btn("Rotate") { cycleRotation() }
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        barView = bar
        lastTouch = SystemClock.elapsedRealtime()
        setContentView(root)
    }

    private fun flipCamera() {
        if (!started || restartPending) return
        facing = if (facing == CameraCharacteristics.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        startSource()
    }

    private fun cycleRotation() {
        rotMode = (rotMode + 1) % 3
        requestedOrientation = when (rotMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        Toast.makeText(this, arrayOf("Auto rotate", "Landscape (best for several players)", "Portrait (best for squats)")[rotMode], Toast.LENGTH_SHORT).show()
    }

    private fun connectWifiAuto() {
        nsd?.stop()
        nsd = NsdFinder(this, { host, port -> prefs.edit().putString("ip", host).apply(); link?.connectWifi(host, port) }, { netStatus = it })
        nsd!!.start()
    }

    private fun askIp() {
        val et = EditText(this); et.inputType = InputType.TYPE_CLASS_TEXT; et.setText(prefs.getString("ip", "192.168."))
        AlertDialog.Builder(this).setTitle("TV IP address (shown on the TV screen)").setView(et)
            .setPositiveButton("Connect") { _, _ ->
                val ip = et.text.toString().trim()
                prefs.edit().putString("ip", ip).apply(); link?.connectWifi(ip)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun pickBt() {
        val devs: List<BluetoothDevice> = try { BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        if (devs.isEmpty()) { netStatus = "No paired Bluetooth devices. Pair this phone with the TV first."; return }
        val names = devs.map { try { it.name ?: it.address } catch (e: Exception) { it.address } }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Pick your TV").setItems(names) { _, i -> link?.connectBt(devs[i]) }.show()
    }

    // ---------- shared ----------
    private fun begin() {
        if (started) return
        started = true
        pose = PoseRunner(this, if (isTv) 2 else 4, ::onLocalPose) { camStatus = it }
        if (isTv) {
            server = TvServer(this, ::onRemotePose, ::onRemoteCmd) { netStatus = it }.also { it.start() }
        } else {
            link = PhoneLink { netStatus = it }.also { it.start() }
            netStatus = "Not connected - tap Wi-Fi, TV IP or Bluetooth"
            prefs.getString("ip", null)?.takeIf { it.count { c -> c == '.' } == 3 }?.let { link?.connectWifi(it) }
        }
        startSource()
        ui.post(object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                val c = camFrames.get(); val p = pose?.poseFrames?.get() ?: 0
                val camFps = c - lastCam; val poseFps = p - lastPose; lastCam = c; lastPose = p
                val input = if (isTv) (if (remoteActive()) "Input: phone" else "Input: TV camera (${if (useUvc) "UVC" else "Camera2"})")
                            else "PHONE camera -> TV"
                val txt = "$input\nCam $camFps fps | Pose $poseFps fps | ${pose?.latencyMs?.toInt() ?: 0} ms | ${pose?.delegateName}\n$netStatus\n$camStatus $crashInfo"
                overlay?.info = txt; game?.info = txt; game?.cameraLabel = if (useUvc) "UVC" else "Camera2"
                overlay?.postInvalidate()
                if (!isTv) {
                    val hide = netStatus.startsWith("Connected") && now - lastTouch > 6000
                    barView?.visibility = if (hide) View.GONE else View.VISIBLE
                }
                ui.postDelayed(this, 1000)
            }
        })
    }

    private fun onLocalPose(pts: List<FloatArray>) {
        if (isTv) { if (!remoteActive()) game?.setFrame(pts) }
        else { overlay?.setPoses(pts); link?.send(pts) }
    }

    private fun onRemoteCmd(cmd: Int) {
        if (cmd == 1) ui.post { game?.togglePause() }
    }

    private fun onRemotePose(pts: List<FloatArray>) {
        remoteTs = SystemClock.elapsedRealtime()
        game?.setFrame(pts)
    }

    /** Stop the old camera, wait a moment so Android can release it, then open the new one. */
    private fun startSource() {
        if (restartPending) return
        restartPending = true
        source?.stop(); source = null
        camFrames.set(0); lastCam = 0
        ui.postDelayed({
            restartPending = false
            if (isFinishing || isDestroyed || !hasCam()) return@postDelayed
            val cb = { bmp: Bitmap, _: Long ->
                camFrames.incrementAndGet()
                if (isTv) { if (!remoteActive()) { game?.setThumb(bmp); pose?.submit(bmp) } }
                else { overlay?.setFrame(bmp); pose?.submit(bmp) }
                Unit
            }
            val st = { s: String -> camStatus = s }
            source = when {
                isTv && useUvc -> UvcSource(this, cb, st)
                isTv -> Camera2Source(this, cb, st)
                else -> Camera2Source(this, cb, st, facing, true)
            }
            source!!.start()
        }, 450)
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (isTv) {
            if (game?.onKey(k) == true) return true
        }
        return super.onKeyDown(k, e)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isTv && game?.onBack() == true) return else super.onBackPressed()
    }

    override fun onDestroy() {
        source?.stop(); pose?.close(); server?.stop(); link?.stop(); nsd?.stop()
        super.onDestroy()
    }
}
