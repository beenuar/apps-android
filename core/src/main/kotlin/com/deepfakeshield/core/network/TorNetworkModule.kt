package com.deepfakeshield.core.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * Observable state for Tor + VPN.
 * Uses Compose mutableStateOf — any Composable reading these auto-recomposes.
 * All writes dispatched to main thread for snapshot safety.
 */
object TorNetworkModule {

    private const val TAG = "PrivacyNet"
    private const val PROXY_HOST = "127.0.0.1"
    private val mainHandler = Handler(Looper.getMainLooper())

    var proxyPort by mutableIntStateOf(9050)
        private set
    var connectionStatus by mutableStateOf("Off")
        private set
    var isConnected by mutableStateOf(false)
        private set
    var mode by mutableStateOf("direct")
        private set
    var enabled by mutableStateOf(false)
        private set
    var vpnActive by mutableStateOf(false)
        internal set

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    fun onTorStarting() = runOnMain {
        enabled = true; mode = "starting"
        connectionStatus = "Connecting to Tor..."; isConnected = false
        Log.i(TAG, "Tor starting")
    }

    fun onTorConnected(socksPort: Int) = runOnMain {
        enabled = true; proxyPort = if (socksPort > 0) socksPort else 9050
        mode = "tor"; connectionStatus = "Connected via Tor"; isConnected = true
        Log.i(TAG, "Tor connected on SOCKS port $proxyPort")
    }

    fun onTorStopped() = runOnMain {
        enabled = false; mode = "direct"; connectionStatus = "Off"
        isConnected = false; vpnActive = false
        Log.i(TAG, "Tor stopped")
    }

    fun onTorStopping() = runOnMain {
        mode = "stopping"; connectionStatus = "Disconnecting..."
        Log.i(TAG, "Tor stopping")
    }

    fun onTorError(message: String) = runOnMain {
        mode = "error"; connectionStatus = "Error: $message"; isConnected = false
        Log.e(TAG, "Tor error: $message")
    }

    fun updateVpnState(active: Boolean) = runOnMain { vpnActive = active }

    fun getProxy(): Proxy {
        if (isConnected) return Proxy(Proxy.Type.SOCKS, InetSocketAddress(PROXY_HOST, proxyPort))
        return Proxy.NO_PROXY
    }

    fun openConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val conn = url.openConnection(getProxy()) as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Cyble/3.0")
        if (isConnected) {
            conn.connectTimeout = 30_000; conn.readTimeout = 60_000
            conn.setRequestProperty("DNT", "1"); conn.setRequestProperty("Sec-GPC", "1")
        } else {
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
        }
        return conn
    }

    fun getExternalIp(): String? = try {
        val conn = openConnection("https://api.ipify.org")
        conn.inputStream.bufferedReader().use { it.readText().trim() }.also { conn.disconnect() }
    } catch (_: Exception) { null }

    fun testConnection(): TorTestResult {
        if (!isConnected) return TorTestResult(false, "Tor is not connected")
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(PROXY_HOST, proxyPort))
            val conn = URL("https://check.torproject.org/api/ip").openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 20_000; conn.readTimeout = 20_000
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val isTor = response.contains("\"IsTor\":true", ignoreCase = true)
            val exitIp = Regex("\"IP\":\"([^\"]+)\"").find(response)?.groupValues?.get(1) ?: "unknown"
            if (isTor) runOnMain { connectionStatus = "Tor Connected — Exit: $exitIp" }
            TorTestResult(true, if (isTor) "Connected via Tor — exit IP: $exitIp" else "Proxy active — IP: $exitIp",
                isTor = isTor, proxyReachable = true, exitIp = exitIp)
        } catch (e: Exception) {
            TorTestResult(false, "Test failed: ${e.message?.take(60)}")
        }
    }

    data class TorTestResult(
        val success: Boolean, val message: String,
        val isTor: Boolean = false, val proxyReachable: Boolean = false, val exitIp: String? = null
    )
}
