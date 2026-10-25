package com.razcorp.posetest

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {
    private val isTv by lazy {
        (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
    private var overlay: OverlayView? = null   // phone preview
    private var game: GameView? = null         // TV game
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
    private val ui = Handler(Looper.getMainLooper())
    private var lastCam = 0
    private var lastPose = 0
    private var started = false
    private val prefs by lazy { getSharedPreferences("posetest", 0) }

    private fun remoteActive() = SystemClock.elapsedRealtime() - remoteTs < 1500

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isTv) { game = GameView(this); setContentView(game) } else buildPhoneUi()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        else begin()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) { begin() }

    // ---------- phone UI ----------
    private fun buildPhoneUi() {
        val root = FrameLayout(this)
        overlay = OverlayView(this); root.addView(overlay)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0x99000000.toInt()) }
        fun btn(t: String, f: () -> Unit) {
            val bt = Button(this); bt.text = t; bt.setOnClickListener { f() }
            bar.addView(bt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        btn("Wi-Fi auto") { connectWifiAuto() }
        btn("TV IP") { askIp() }
        btn("Bluetooth") { pickBt() }
        btn("Flip cam") {
            facing = if (facing == CameraCharacteristics.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            startSource()
        }
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
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
        if (devs.isEmpty()) { netStatus = "No paired Bluetooth devices. Pair this phone with the TV in system Bluetooth settings first."; return }
        val names = devs.map { try { it.name ?: it.address } catch (e: Exception) { it.address } }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Pick your TV").setItems(names) { _, i -> link?.connectBt(devs[i]) }.show()
    }

    // ---------- shared ----------
    private fun begin() {
        if (started) return
        started = true
        pose = PoseRunner(this, ::onLocalPose) { camStatus = it }
        if (isTv) {
            server = TvServer(this, ::onRemotePose) { netStatus = it }.also { it.start() }
        } else {
            link = PhoneLink { netStatus = it }.also { it.start() }
            netStatus = "Not connected - tap Wi-Fi auto, TV IP or Bluetooth"
            prefs.getString("ip", null)?.takeIf { it.count { c -> c == '.' } == 3 }?.let { link?.connectWifi(it) }
        }
        startSource()
        ui.post(object : Runnable {
            override fun run() {
                val c = camFrames.get(); val p = pose?.poseFrames?.get() ?: 0
                val camFps = c - lastCam; val poseFps = p - lastPose; lastCam = c; lastPose = p
                val input = if (isTv) (if (remoteActive()) "PHONE" else "TV camera (${if (useUvc) "UVC" else "Camera2"}) [OK = switch]") else "this phone's camera -> TV"
                val txt = "${if (isTv) "TV" else "PHONE"} mode | input: $input\n" +
                    "Cam $camFps fps | Pose $poseFps fps | ${pose?.latencyMs?.toInt() ?: 0} ms | ${pose?.delegateName}\n$netStatus\n$camStatus"
                overlay?.info = txt; game?.info = txt
                overlay?.postInvalidate()
                ui.postDelayed(this, 1000)
            }
        })
    }

    private fun onLocalPose(pts: FloatArray?) {
        if (isTv) { if (!remoteActive()) game?.setPose(pts) }
        else { overlay?.setPose(pts); link?.send(pts) }
    }

    private fun onRemotePose(pts: FloatArray?) {
        remoteTs = SystemClock.elapsedRealtime()
        game?.setPose(pts)
    }

    private fun startSource() {
        source?.stop()
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
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (isTv && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
            useUvc = !useUvc; camFrames.set(0); lastCam = 0; startSource(); return true
        }
        return super.onKeyDown(k, e)
    }

    override fun onDestroy() {
        source?.stop(); pose?.close(); server?.stop(); link?.stop(); nsd?.stop()
        super.onDestroy()
    }
}
