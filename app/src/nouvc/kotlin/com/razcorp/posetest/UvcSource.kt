package com.razcorp.posetest

import android.app.Activity
import android.graphics.Bitmap

class UvcSource(
    private val act: Activity,
    private val onFrame: (Bitmap, Long) -> Unit,
    private val onStatus: (String) -> Unit
) : FrameSource {
    override fun start() { onStatus("UVC not included in this APK. Install the '-uvc' APK build.") }
    override fun stop() {}
}
