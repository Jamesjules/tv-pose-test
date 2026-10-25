package com.razcorp.posetest

import android.graphics.Bitmap
import android.media.Image

object YuvUtil {
    private fun pack(y: Int, u: Int, v: Int): Int {
        val c = y - 16; val d = u - 128; val e = v - 128
        var r = (298 * c + 409 * e + 128) shr 8
        var g = (298 * c - 100 * d - 208 * e + 128) shr 8
        var b = (298 * c + 516 * d + 128) shr 8
        r = r.coerceIn(0, 255); g = g.coerceIn(0, 255); b = b.coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Camera2 YUV_420_888 -> Bitmap (handles row/pixel strides). */
    fun yuv420ToBitmap(img: Image): Bitmap {
        val w = img.width; val h = img.height
        val yp = img.planes[0]; val up = img.planes[1]; val vp = img.planes[2]
        val yb = yp.buffer; val ub = up.buffer; val vb = vp.buffer
        val out = IntArray(w * h)
        for (j in 0 until h) {
            val yRow = j * yp.rowStride
            val uRow = (j shr 1) * up.rowStride
            val vRow = (j shr 1) * vp.rowStride
            for (i in 0 until w) {
                val y = yb.get(yRow + i * yp.pixelStride).toInt() and 0xFF
                val u = ub.get(uRow + (i shr 1) * up.pixelStride).toInt() and 0xFF
                val v = vb.get(vRow + (i shr 1) * vp.pixelStride).toInt() and 0xFF
                out[j * w + i] = pack(y, u, v)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** NV21 byte array (Y plane then interleaved VU) -> Bitmap. */
    fun nv21ToBitmap(data: ByteArray, w: Int, h: Int): Bitmap {
        val out = IntArray(w * h)
        val base = w * h
        for (j in 0 until h) {
            for (i in 0 until w) {
                val y = data[j * w + i].toInt() and 0xFF
                val idx = base + (j shr 1) * w + (i and 1.inv())
                val v = data[idx].toInt() and 0xFF
                val u = data[idx + 1].toInt() and 0xFF
                out[j * w + i] = pack(y, u, v)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
