package com.razcorp.posetest

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {
    private lateinit var overlay: OverlayView
    private var pose: PoseRunner? = null
    private var source: FrameSource? = null
    private var useUvc = false
    private val camFrames = AtomicInteger()
    @Volatile private var status = "Starting..."
    private val ui = Handler(Looper.getMainLooper())
    private var lastCam = 0
    private var lastPose = 0
    private var camFps = 0
    private var poseFps = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        overlay = OverlayView(this)
        setContentView(overlay)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        else begin()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) { begin() }

    private var started = false
    private fun begin() {
        if (started) return
        started = true
        pose = PoseRunner(this, { overlay.setPose(it) }, ::setStatus)
        startSource()
        ui.post(object : Runnable {
            override fun run() {
                val c = camFrames.get(); val p = pose?.poseFrames?.get() ?: 0
                camFps = c - lastCam; poseFps = p - lastPose; lastCam = c; lastPose = p
                overlay.info = "Source: ${if (useUvc) "UVC (USB)" else "Camera2"}   [OK button = switch]\n" +
                    "Camera ${camFps} fps | Pose ${poseFps} fps | Latency ${pose?.latencyMs?.toInt() ?: 0} ms | ${pose?.delegateName}\n" + status
                overlay.postInvalidate()
                ui.postDelayed(this, 1000)
            }
        })
    }

    private fun setStatus(s: String) { status = s }

    private fun startSource() {
        source?.stop()
        val cb = { bmp: Bitmap, _: Long ->
            camFrames.incrementAndGet(); overlay.setFrame(bmp); pose?.submit(bmp); Unit
        }
        source = if (useUvc) UvcSource(this, cb, ::setStatus) else Camera2Source(this, cb, ::setStatus)
        source!!.start()
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            useUvc = !useUvc; camFrames.set(0); lastCam = 0; startSource(); return true
        }
        return super.onKeyDown(k, e)
    }

    override fun onDestroy() { source?.stop(); pose?.close(); super.onDestroy() }
}
