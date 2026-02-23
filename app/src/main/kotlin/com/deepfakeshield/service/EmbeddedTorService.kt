package com.deepfakeshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.deepfakeshield.MainActivity
import com.deepfakeshield.R
import com.deepfakeshield.core.network.TorNetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.torproject.jni.TorService
import java.io.PrintWriter
import java.util.concurrent.Executor

class DeepfakeTorService : TorService() {

    companion object {
        const val CHANNEL_ID = "tor_service_channel"
        private const val NOTIFICATION_ID = 9050

        fun writeTorrc(context: Context, exitCountry: String) {
            try {
                val torrc = getTorrc(context)
                torrc.parentFile?.mkdirs()
                val lines = mutableListOf<String>()
                if (exitCountry != "auto" && exitCountry.length == 2) {
                    lines.add("ExitNodes {${exitCountry.lowercase()}}")
                    lines.add("StrictNodes 1")
                }
                PrintWriter(torrc).use { pw -> lines.forEach { pw.println(it) } }
                Log.i("DeepfakeTor", "torrc: exit=$exitCountry")
            } catch (e: Exception) {
                Log.w("DeepfakeTor", "writeTorrc failed: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        createChannel()
        val notification = buildNotification("Connecting to Tor...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("DeepfakeTor", "startForeground failed: ${e.message}")
        }
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Tor Privacy", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Active while Tor protects your privacy"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tor Privacy Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true).setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

/**
 * Manages embedded Tor lifecycle.
 *
 * CRITICAL: Always uses applicationContext internally for bind/unbind
 * so that start() and stop() can be called from ANY context (Activity,
 * Service, BroadcastReceiver) without bind/unbind context mismatch.
 */
object EmbeddedTorManager {

    private const val TAG = "EmbeddedTor"
    private const val CONNECT_TIMEOUT_MS = 60_000L

    private val _status = MutableStateFlow(TorStatus.OFF)
    val status: StateFlow<TorStatus> = _status.asStateFlow()

    private val _socksPort = MutableStateFlow(-1)
    val socksPort: StateFlow<Int> = _socksPort.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var isBound = false
    private var boundContext: Context? = null
    private var statusReceiver: BroadcastReceiver? = null
    private var errorReceiver: BroadcastReceiver? = null
    var currentExitCountry: String = "auto"
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    enum class TorStatus { OFF, STARTING, ON, STOPPING, ERROR }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "TorService bound")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "TorService unbound unexpectedly")
            isBound = false
            boundContext = null
            if (_status.value != TorStatus.OFF && _status.value != TorStatus.ERROR) {
                _status.value = TorStatus.ERROR
                _errorMessage.value = "Tor service crashed"
                TorNetworkModule.onTorError("Service disconnected unexpectedly")
            }
            _socksPort.value = -1
        }
    }

    fun start(context: Context, exitCountry: String = "auto") {
        if (_status.value == TorStatus.ON || _status.value == TorStatus.STARTING) return

        val appCtx = context.applicationContext
        currentExitCountry = exitCountry
        _errorMessage.value = null
        _status.value = TorStatus.STARTING
        TorNetworkModule.onTorStarting()

        DeepfakeTorService.writeTorrc(appCtx, exitCountry)
        registerReceivers(appCtx)
        startTimeout(appCtx)

        try {
            val intent = Intent(appCtx, DeepfakeTorService::class.java).apply {
                action = TorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appCtx.startForegroundService(intent)
            } else {
                appCtx.startService(intent)
            }
            appCtx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            isBound = true
            boundContext = appCtx
            Log.i(TAG, "TorService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start: ${e.message}", e)
            cancelTimeout()
            _status.value = TorStatus.ERROR
            _errorMessage.value = "Failed to start Tor: ${e.message?.take(100)}"
            TorNetworkModule.onTorError(_errorMessage.value ?: "Unknown error")
        }
    }

    fun stop(context: Context) {
        cancelTimeout()
        if (_status.value == TorStatus.OFF) return

        val appCtx = context.applicationContext
        _status.value = TorStatus.STOPPING
        TorNetworkModule.onTorStopping()

        try {
            stopVpn(appCtx)
            clearWebViewProxy()
            unregisterReceivers(appCtx)

            if (isBound) {
                val ctx = boundContext ?: appCtx
                try { ctx.unbindService(serviceConnection) } catch (e: Exception) {
                    Log.w(TAG, "unbind failed on stored ctx, trying appCtx: ${e.message}")
                    try { appCtx.unbindService(serviceConnection) } catch (_: Exception) {}
                }
                isBound = false
                boundContext = null
            }

            appCtx.stopService(Intent(appCtx, DeepfakeTorService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}")
        }

        _status.value = TorStatus.OFF
        _socksPort.value = -1
        _errorMessage.value = null
        TorNetworkModule.onTorStopped()
        Log.i(TAG, "Tor stopped")
    }

    fun isRunning(): Boolean = _status.value == TorStatus.ON

    private fun startVpn(context: Context) {
        val vpnIntent = android.net.VpnService.prepare(context)
        if (vpnIntent != null) {
            TorVpnConsentActivity.launch(context)
        } else {
            TorVpnService.start(context)
        }
        handler.postDelayed({ TorNetworkModule.updateVpnState(TorVpnService.isRunning) }, 2000)
    }

    private fun stopVpn(context: Context) {
        TorVpnService.stop(context)
        TorNetworkModule.updateVpnState(false)
    }

    private fun startTimeout(appCtx: Context) {
        cancelTimeout()
        timeoutRunnable = Runnable {
            if (_status.value == TorStatus.STARTING) {
                Log.w(TAG, "Connection timeout")
                _status.value = TorStatus.ERROR
                _errorMessage.value = "Connection timed out — check internet"
                TorNetworkModule.onTorError("Connection timed out")
                stop(appCtx)
            }
        }
        handler.postDelayed(timeoutRunnable!!, CONNECT_TIMEOUT_MS)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun setWebViewProxy(socksPort: Int) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().setProxyOverride(
                    ProxyConfig.Builder().addProxyRule("socks5://127.0.0.1:$socksPort").build(),
                    Executor { it.run() },
                    Runnable { Log.i(TAG, "WebView proxy → socks5://127.0.0.1:$socksPort") }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "WebView proxy set failed: ${e.message}")
        }
    }

    private fun clearWebViewProxy() {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(
                    Executor { it.run() }, Runnable { Log.i(TAG, "WebView proxy cleared") }
                )
            }
        } catch (_: Exception) {}
    }

    private fun registerReceivers(appCtx: Context) {
        if (statusReceiver != null) return

        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val st = intent?.getStringExtra(TorService.EXTRA_STATUS) ?: return
                val pkg = intent.getStringExtra(TorService.EXTRA_SERVICE_PACKAGE_NAME)
                if (pkg != null && pkg != appCtx.packageName) return

                Log.i(TAG, "Tor status: $st")
                when (st) {
                    TorService.STATUS_ON -> {
                        cancelTimeout()
                        _status.value = TorStatus.ON
                        _errorMessage.value = null
                        val port = TorService.socksPort
                        _socksPort.value = port
                        Log.i(TAG, "Tor CONNECTED — SOCKS5 :$port")
                        TorNetworkModule.onTorConnected(port)
                        setWebViewProxy(port)
                        startVpn(appCtx)
                    }
                    TorService.STATUS_OFF -> {
                        cancelTimeout()
                        _status.value = TorStatus.OFF
                        _socksPort.value = -1
                        TorNetworkModule.onTorStopped()
                        clearWebViewProxy()
                        stopVpn(appCtx)
                    }
                    TorService.STATUS_STARTING -> _status.value = TorStatus.STARTING
                    TorService.STATUS_STOPPING -> _status.value = TorStatus.STOPPING
                }
            }
        }

        errorReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val errorText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: "Unknown Tor error"
                Log.e(TAG, "Tor ERROR: $errorText")
                cancelTimeout()
                _status.value = TorStatus.ERROR
                _errorMessage.value = errorText
                TorNetworkModule.onTorError(errorText)
            }
        }

        val statusFilter = IntentFilter(TorService.ACTION_STATUS)
        val errorFilter = IntentFilter(TorService.ACTION_ERROR)

        LocalBroadcastManager.getInstance(appCtx).registerReceiver(statusReceiver!!, statusFilter)
        LocalBroadcastManager.getInstance(appCtx).registerReceiver(errorReceiver!!, errorFilter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appCtx.registerReceiver(statusReceiver!!, statusFilter, Context.RECEIVER_NOT_EXPORTED)
            appCtx.registerReceiver(errorReceiver!!, errorFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appCtx.registerReceiver(statusReceiver!!, statusFilter)
            appCtx.registerReceiver(errorReceiver!!, errorFilter)
        }
    }

    private fun unregisterReceivers(appCtx: Context) {
        listOf(statusReceiver, errorReceiver).forEach { receiver ->
            receiver?.let {
                try { LocalBroadcastManager.getInstance(appCtx).unregisterReceiver(it) } catch (_: Exception) {}
                try { appCtx.unregisterReceiver(it) } catch (_: Exception) {}
            }
        }
        statusReceiver = null
        errorReceiver = null
    }
}
