package com.razcorp.posetest

import android.app.Activity
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.serenegiant.usb.USBMonitor

/** Direct USB (UVC) access via AndroidUSBCamera (libausbc). Best-effort: bypasses the Android camera API. */
class UvcSource(
    private val act: Activity,
    private val onFrame: (Bitmap, Long) -> Unit,
    private val onStatus: (String) -> Unit
) : FrameSource {

    private var client: MultiCameraClient? = null
    private val cams = HashMap<Int, MultiCameraClient.ICamera>()
    private var tex: AspectRatioTextureView? = null

    override fun start() {
        try {
            // Tiny hidden texture view; the library wants a surface even though we draw frames ourselves.
            tex = AspectRatioTextureView(act)
            act.addContentView(tex, FrameLayout.LayoutParams(2, 2))

            client = MultiCameraClient(act, object : IDeviceConnectCallBack {
                override fun onAttachDev(device: UsbDevice?) {
                    device ?: return
                    onStatus("UVC: device attached ${device.deviceName} (${device.productName}) - asking permission")
                    if (!cams.containsKey(device.deviceId)) cams[device.deviceId] = CameraUVC(act, device)
                    client?.requestPermission(device)
                }
                override fun onDetachDec(device: UsbDevice?) { onStatus("UVC: device detached") }
                override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                    device ?: return; ctrlBlock ?: return
                    val cam = cams[device.deviceId] as? CameraUVC ?: return
                    cam.setUsbControlBlock(ctrlBlock)
                    cam.addPreviewDataCallBack(object : IPreviewDataCallBack {
                        override fun onPreviewData(data: ByteArray?, width: Int, height: Int, format: IPreviewDataCallBack.DataFormat) {
                            data ?: return
                            onFrame(YuvUtil.nv21ToBitmap(data, width, height), SystemClock.elapsedRealtime())
                        }
                    })
                    val req = CameraRequest.Builder().setPreviewWidth(640).setPreviewHeight(480).create()
                    cam.openCamera(tex, req)
                    onStatus("UVC: opened ${device.productName} 640x480")
                }
                override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                    device?.let { (cams[it.deviceId] as? CameraUVC)?.closeCamera() }
                }
                override fun onCancelDev(device: UsbDevice?) { onStatus("UVC: USB permission denied") }
            })
            client!!.register()
            onStatus("UVC: waiting for a USB camera... plug it in / re-plug it")
        } catch (e: Throwable) {
            onStatus("UVC failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun stop() {
        try { cams.values.forEach { (it as? CameraUVC)?.closeCamera() } } catch (_: Throwable) {}
        try { client?.unRegister(); client?.destroy() } catch (_: Throwable) {}
        tex?.let { (it.parent as? ViewGroup)?.removeView(it) }
        cams.clear(); client = null; tex = null
    }
}
