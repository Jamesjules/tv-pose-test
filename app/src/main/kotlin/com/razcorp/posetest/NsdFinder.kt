package com.razcorp.posetest

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class NsdFinder(
    ctx: Context,
    private val onFound: (String, Int) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var disc: NsdManager.DiscoveryListener? = null
    @Volatile private var done = false

    fun start() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) { onStatus("Searching for TV on Wi-Fi...") }
            override fun onServiceFound(s: NsdServiceInfo) {
                if (done) return
                nsd.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, code: Int) { onStatus("Auto-find failed ($code) - use the IP button") }
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        val h = si.host?.hostAddress ?: return
                        if (!done) { done = true; onFound(h, si.port); stop() }
                    }
                })
            }
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, c: Int) { onStatus("Auto-find failed ($c) - use the IP button") }
            override fun onStopDiscoveryFailed(t: String, c: Int) {}
        }
        disc = l
        nsd.discoverServices("_razpose._tcp.", NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun stop() {
        try { disc?.let { nsd.stopServiceDiscovery(it) } } catch (_: Exception) {}
        disc = null
    }
}
