package com.razcorp.posetest

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections
import java.util.UUID
import kotlin.concurrent.thread

/** TV side: listens for a phone over Wi-Fi (TCP, auto-discoverable) and Bluetooth (RFCOMM). */
class TvServer(
    private val ctx: Context,
    private val onPose: (List<FloatArray>) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        const val PORT = 47800
        val BT_UUID: UUID = UUID.fromString("8f4c1a52-7b6e-4d0a-9d52-3b0c7a1e5a10")
    }

    @Volatile private var running = false
    private var tcp: ServerSocket? = null
    private var btServer: android.bluetooth.BluetoothServerSocket? = null
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null

    private fun localIp(): String = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress ?: "no network"
    } catch (e: Exception) { "?" }

    private fun btName(): String = try { BluetoothAdapter.getDefaultAdapter()?.name ?: "no Bluetooth" } catch (e: Exception) { "?" }

    private fun waiting() = "Waiting for phone... Wi-Fi ${localIp()}"

    fun start() {
        running = true
        onStatus(waiting())
        startTcp(); startBt(); registerNsd()
    }

    private fun startTcp() = thread(isDaemon = true) {
        try {
            val ss = ServerSocket(PORT); tcp = ss
            while (running) {
                val s = ss.accept(); s.tcpNoDelay = true
                thread(isDaemon = true) { serve("Wi-Fi", s.getInputStream()); try { s.close() } catch (_: Exception) {} }
            }
        } catch (e: Exception) { if (running) onStatus("Wi-Fi server error: ${e.message}") }
    }

    private fun startBt() = thread(isDaemon = true) {
        try {
            val ad = BluetoothAdapter.getDefaultAdapter() ?: return@thread
            if (!ad.isEnabled) return@thread
            val ss = ad.listenUsingRfcommWithServiceRecord("RazPose", BT_UUID); btServer = ss
            while (running) {
                val s = ss.accept()
                thread(isDaemon = true) { serve("Bluetooth", s.inputStream); try { s.close() } catch (_: Exception) {} }
            }
        } catch (e: Exception) { if (running) onStatus("Bluetooth server error: ${e.message}") }
    }

    private fun registerNsd() {
        try {
            val m = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager; nsd = m
            val info = NsdServiceInfo().apply { serviceName = "RazPose"; serviceType = "_razpose._tcp."; port = PORT }
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, c: Int) {}
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, c: Int) {}
            }
            regListener = l
            m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (_: Exception) {}
    }

    private fun serve(name: String, ins: InputStream) {
        onStatus("Phone connected via $name")
        try {
            val d = DataInputStream(BufferedInputStream(ins))
            while (running) {
                val magic = d.readByte().toInt()
                if (magic == 0x52) {            // v1: one person
                    d.readLong()
                    val n = d.readUnsignedByte()
                    onPose(if (n == 0) emptyList() else listOf(FloatArray(n * 2) { d.readShort() / 10000f }))
                } else if (magic == 0x53) {     // v2: up to 4 people
                    d.readLong()
                    val cnt = d.readUnsignedByte()
                    val list = ArrayList<FloatArray>()
                    repeat(cnt) {
                        val n = d.readUnsignedByte()
                        list.add(FloatArray(n * 2) { d.readShort() / 10000f })
                    }
                    onPose(list)
                } else break
            }
        } catch (_: Exception) {}
        onStatus(waiting())
    }

    fun stop() {
        running = false
        try { tcp?.close() } catch (_: Exception) {}
        try { btServer?.close() } catch (_: Exception) {}
        try { regListener?.let { nsd?.unregisterService(it) } } catch (_: Exception) {}
    }
}
