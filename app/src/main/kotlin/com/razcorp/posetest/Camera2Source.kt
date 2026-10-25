package com.razcorp.posetest

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

/** Standard Android camera API. Works only if the TV firmware exposes the USB webcam as an "external" camera. */
class Camera2Source(
    private val ctx: Context,
    private val onFrame: (Bitmap, Long) -> Unit,
    private val onStatus: (String) -> Unit
) : FrameSource {

    private val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    @SuppressLint("MissingPermission")
    override fun start() {
        try {
            val ids = mgr.cameraIdList
            if (ids.isEmpty()) {
                onStatus("Camera2: NO cameras exposed by Android (USB webcam not visible to the camera API). Press OK to try UVC.")
                return
            }
            val id = ids.firstOrNull {
                mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL
            } ?: ids[0]
            onStatus("Camera2: ids=[${ids.joinToString()}] opening $id")

            val chars = mgr.getCameraCharacteristics(id)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizes = map.getOutputSizes(ImageFormat.YUV_420_888)
            val size = sizes.filter { it.width <= 800 }.maxByOrNull { it.width * it.height }
                ?: sizes.minByOrNull { it.width * it.height }!!

            thread = HandlerThread("cam2").also { it.start() }
            handler = Handler(thread!!.looper)

            reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).also { r ->
                r.setOnImageAvailableListener({ rd ->
                    val img = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try { onFrame(YuvUtil.yuv420ToBitmap(img), SystemClock.elapsedRealtime()) }
                    finally { img.close() }
                }, handler)
            }

            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    device = cam
                    @Suppress("DEPRECATION")
                    cam.createCaptureSession(listOf(reader!!.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            session = s
                            val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            req.addTarget(reader!!.surface)
                            s.setRepeatingRequest(req.build(), null, handler)
                            onStatus("Camera2: streaming ${size.width}x${size.height}")
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) { onStatus("Camera2: session config failed") }
                    }, handler)
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close(); onStatus("Camera2: disconnected") }
                override fun onError(cam: CameraDevice, e: Int) { cam.close(); onStatus("Camera2: error code $e") }
            }, handler)
        } catch (e: Throwable) {
            onStatus("Camera2 failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun stop() {
        try { session?.close() } catch (_: Throwable) {}
        try { device?.close() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        thread?.quitSafely()
        session = null; device = null; reader = null; thread = null; handler = null
    }
}
