package io.github.aacreat.omniproxy.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.aacreat.omniproxy.OmniProxyApplication
import io.github.aacreat.omniproxy.MainActivity
import io.github.aacreat.omniproxy.R
import io.github.aacreat.omniproxy.core.data.PreferencesManager
import io.github.aacreat.omniproxy.core.jni.NativeProxy
import io.github.aacreat.omniproxy.core.model.ProxyStats
import io.github.aacreat.omniproxy.core.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OmniProxyService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var statsJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private lateinit var prefs: PreferencesManager
    private lateinit var networkManager: NetworkManager

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        networkManager = NetworkManager(this)
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopProxyService()
            return START_NOT_STICKY
        }

        startForeground(OmniProxyApplication.NOTIFICATION_ID, buildNotification("正在启动代理服务..."))
        serviceScope.launch {
            val config = prefs.configFlow.first()
            val host = if (config.bindAddress == "0.0.0.0") "0.0.0.0" else config.bindAddress

            if (config.httpEnabled) {
                NativeProxy.startHttp(host, config.httpPort, config.username, config.password)
            } else {
                NativeProxy.stopHttp()
            }

            if (config.socksEnabled) {
                NativeProxy.startSocks5(host, config.socksPort, config.username, config.password)
            } else {
                NativeProxy.stopSocks5()
            }

            if (config.pacEnabled) {
                val recommendHost = networkManager.getRecommendedHost()
                NativeProxy.startPac(host, config.pacPort, recommendHost, config.httpPort, config.socksPort)
            } else {
                NativeProxy.stopPac()
            }

            _isRunningState.value = config.httpEnabled || config.socksEnabled || config.pacEnabled

            startStatsPolling()
        }

        return START_STICKY
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive) {
                val stats = NativeProxy.getStats()
                _statsState.value = stats
                val content = "连接: ${stats.activeConnections} | 上传: ${formatBytes(stats.bytesUploaded)} | 下载: ${formatBytes(stats.bytesDownloaded)}"
                val notification = buildNotification(content)
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(OmniProxyApplication.NOTIFICATION_ID, notification)
                delay(1000)
            }
        }
    }

    private fun stopProxyService() {
        NativeProxy.stopHttp()
        NativeProxy.stopSocks5()
        NativeProxy.stopPac()
        NativeProxy.reset()
        _isRunningState.value = false
        statsJob?.cancel()
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OmniProxy:WakeLock").apply {
            acquire(10 * 60 * 1000L /* 10 mins auto-renew */)
        }

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "OmniProxy:WifiLock").apply {
            acquire()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OmniProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, OmniProxyApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_proxy)
            .setContentTitle("Every Proxy 运行�?)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_tile_proxy, "停止服务", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProxyService()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "io.github.aacreat.omniproxy.START"
        const val ACTION_STOP = "io.github.aacreat.omniproxy.STOP"

        private val _isRunningState = MutableStateFlow(false)
        val isRunningState: StateFlow<Boolean> = _isRunningState.asStateFlow()

        private val _statsState = MutableStateFlow(ProxyStats())
        val statsState: StateFlow<ProxyStats> = _statsState.asStateFlow()

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
            val pre = "KMGTPE"[exp - 1]
            return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
        }

        fun start(context: Context) {
            val intent = Intent(context, OmniProxyService::class.java).apply {
                action = ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OmniProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
