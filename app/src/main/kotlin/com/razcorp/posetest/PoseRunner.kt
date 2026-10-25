package com.razcorp.posetest

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.concurrent.atomic.AtomicInteger

class PoseRunner(
    ctx: Context,
    private val numPoses: Int,
    private val onPose: (List<FloatArray>) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var lm: PoseLandmarker? = null
    @Volatile private var busy = false
    @Volatile private var submitAt = 0L
    private var lastTs = 0L
    @Volatile var delegateName = "none"
    @Volatile var latencyMs = 0f
    val poseFrames = AtomicInteger()

    init {
        lm = build(ctx, Delegate.GPU) ?: build(ctx, Delegate.CPU)
        if (lm == null) onStatus("Pose model failed to load (is pose_landmarker_lite.task in assets?)")
    }

    private fun build(ctx: Context, d: Delegate): PoseLandmarker? = try {
        val base = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .setDelegate(d).build()
        val opts = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(numPoses)
            .setResultListener { result, _ ->
                val now = SystemClock.elapsedRealtime()
                latencyMs = latencyMs * 0.8f + (now - submitAt) * 0.2f
                poseFrames.incrementAndGet()
                val list = result.landmarks().map { pts ->
                    val arr = FloatArray(pts.size * 2)
                    pts.forEachIndexed { i, p -> arr[i * 2] = p.x(); arr[i * 2 + 1] = p.y() }
                    arr
                }
                onPose(list)
                busy = false
            }
            .setErrorListener { e -> onStatus("Pose error: ${e.message}"); busy = false }
            .build()
        val l = PoseLandmarker.createFromOptions(ctx, opts)
        delegateName = d.name
        l
    } catch (e: Throwable) {
        onStatus("Pose init with $d failed: ${e.message}")
        null
    }

    fun submit(bmp: Bitmap) {
        val l = lm ?: return
        if (busy) return
        busy = true
        submitAt = SystemClock.elapsedRealtime()
        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTs) ts = lastTs + 1
        lastTs = ts
        try { l.detectAsync(BitmapImageBuilder(bmp).build(), ts) }
        catch (e: Throwable) { onStatus("detectAsync failed: ${e.message}"); busy = false }
    }

    fun close() { try { lm?.close() } catch (_: Throwable) {} }
}
