package com.razcorp.posetest

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

/**
 * Standard Android camera API.
 * TV: facing = null -> prefers an "external" (USB) camera, else first camera.
 * Phone: facing = LENS_FACING_BACK/FRONT and rotate = true so the person is upright for pose tracking.
 * Every callback checks `closed`, so stopping while the camera is still opening can never crash.
 */
class Camera2Source(
    private val ctx: Context,
    private val onFrame: (Bitmap, Long) -> Unit,
    private val onStatus: (String) -> Unit,
    private val facing: Int? = null,
    private val rotate: Boolean = false
) : FrameSource {

    private val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    @Volatile private var closed = false
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var sensor = 0
    private var isFront = false
    private var isExternal = false

    private fun rotationDeg(): Int {
        if (!rotate || isExternal) return 0
        val disp = ((ctx as? Activity)?.windowManager?.defaultDisplay?.rotation ?: 0) * 90
        return if (isFront) (sensor + disp) % 360 else (sensor - disp + 360) % 360
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        closed = false
        try {
            val ids = mgr.cameraIdList
            if (ids.isEmpty()) {
                onStatus("Camera2: no cameras exposed by Android (USB webcam not visible to the camera API).")
                return
            }
            val id = (if (facing != null) ids.firstOrNull { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing } else null)
                ?: ids.firstOrNull { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL }
                ?: ids[0]
            val chars = mgr.getCameraCharacteristics(id)
            val lf = chars.get(CameraCharacteristics.LENS_FACING)
            isFront = lf == CameraCharacteristics.LENS_FACING_FRONT
            isExternal = lf == CameraCharacteristics.LENS_FACING_EXTERNAL
            sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizes = map.getOutputSizes(ImageFormat.YUV_420_888)
            val size = sizes.filter { it.width <= 800 }.maxByOrNull { it.width * it.height }
                ?: sizes.minByOrNull { it.width * it.height }!!

            val th = HandlerThread("cam2").also { it.start() }
            thread = th
            val hd = Handler(th.looper)
            val rd = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
            reader = rd
            rd.setOnImageAvailableListener({ r ->
                if (closed) return@setOnImageAvailableListener
                try {
                    val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        var bmp = YuvUtil.yuv420ToBitmap(img)
                        val deg = rotationDeg()
                        if (deg != 0) {
                            val m = Matrix(); m.postRotate(deg.toFloat())
                            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                        }
                        if (!closed) onFrame(bmp, SystemClock.elapsedRealtime())
                    } finally { img.close() }
                } catch (_: Throwable) {}
            }, hd)

            onStatus("Camera2: opening camera $id")
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    if (closed) { try { cam.close() } catch (_: Throwable) {}; return }
                    device = cam
                    try {
                        @Suppress("DEPRECATION")
                        cam.createCaptureSession(listOf(rd.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                if (closed) { try { s.close() } catch (_: Throwable) {}; return }
                                session = s
                                try {
                                    val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    req.addTarget(rd.surface)
                                    s.setRepeatingRequest(req.build(), null, hd)
                                    onStatus("Camera2: streaming ${size.width}x${size.height}")
                                } catch (e: Throwable) { onStatus("Camera2 start error: ${e.message}") }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) { if (!closed) onStatus("Camera2: session config failed") }
                        }, hd)
                    } catch (e: Throwable) { onStatus("Camera2 session error: ${e.message}") }
                }
                override fun onDisconnected(cam: CameraDevice) { try { cam.close() } catch (_: Throwable) {}; if (!closed) onStatus("Camera2: disconnected") }
                override fun onError(cam: CameraDevice, e: Int) { try { cam.close() } catch (_: Throwable) {}; if (!closed) onStatus("Camera2: error code $e") }
            }, hd)
        } catch (e: Throwable) {
            onStatus("Camera2 failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun stop() {
        closed = true
        try { session?.close() } catch (_: Throwable) {}
        try { device?.close() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        try { thread?.quitSafely() } catch (_: Throwable) {}
        session = null; device = null; reader = null; thread = null
    }
}
