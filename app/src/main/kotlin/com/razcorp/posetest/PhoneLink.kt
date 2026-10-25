package com.razcorp.posetest

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import java.io.Closeable
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/** Phone side: sends the skeleton (33 points) to the TV over Wi-Fi or Bluetooth, reconnects automatically. */
class PhoneLink(private val onStatus: (String) -> Unit) {
    sealed class Target {
        data class Wifi(val host: String, val port: Int) : Target()
        data class Bt(val dev: BluetoothDevice) : Target()
    }

    @Volatile private var target: Target? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var closer: Closeable? = null
    @Volatile private var running = false

    fun start() {
        running = true
        thread(isDaemon = true) {
            while (running) {
                val t = target
                if (t == null || out != null) { Thread.sleep(500); continue }
                try { connect(t) }
                catch (e: Exception) { onStatus("Link: cannot reach TV (${e.message}) - retrying"); drop(); Thread.sleep(2000) }
            }
        }
    }

    private fun connect(t: Target) {
        when (t) {
            is Target.Wifi -> {
                onStatus("Connecting Wi-Fi to ${t.host}...")
                val s = Socket(); s.connect(InetSocketAddress(t.host, t.port), 4000); s.tcpNoDelay = true
                closer = s; out = s.getOutputStream()
                onStatus("Connected via Wi-Fi to ${t.host}")
            }
            is Target.Bt -> {
                onStatus("Connecting Bluetooth...")
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
                val s = t.dev.createRfcommSocketToServiceRecord(TvServer.BT_UUID)
                s.connect(); closer = s; out = s.outputStream
                onStatus("Connected via Bluetooth")
            }
        }
    }

    fun connectWifi(host: String, port: Int = TvServer.PORT) { drop(); target = Target.Wifi(host, port) }
    fun connectBt(dev: BluetoothDevice) { drop(); target = Target.Bt(dev) }

    private fun drop() {
        try { closer?.close() } catch (_: Exception) {}
        out = null; closer = null
    }

    fun send(p: FloatArray?) {
        val o = out ?: return
        val n = (p?.size ?: 0) / 2
        val bb = ByteBuffer.allocate(10 + n * 4)
        bb.put(0x52.toByte()); bb.putLong(SystemClock.elapsedRealtime()); bb.put(n.toByte())
        if (p != null) for (v in p) bb.putShort((v * 10000f).toInt().coerceIn(-32767, 32767).toShort())
        try { synchronized(this) { o.write(bb.array()); o.flush() } }
        catch (e: Exception) { drop(); onStatus("Link lost - reconnecting...") }
    }

    fun stop() { running = false; drop() }
}
