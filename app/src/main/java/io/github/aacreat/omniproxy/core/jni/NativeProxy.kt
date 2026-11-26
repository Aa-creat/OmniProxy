package io.github.aacreat.omniproxy.core.jni

import android.util.Log
import io.github.aacreat.omniproxy.core.model.ProxyStats
import org.json.JSONObject

object NativeProxy {
    private const val TAG = "NativeProxy"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("omniproxy")
            isLoaded = true
            Log.i(TAG, "libomniproxy.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Failed to load libomniproxy.so: ${e.message}. Using safe fallback.")
            isLoaded = false
        }
    }

    fun isNativeReady(): Boolean = isLoaded

    fun startHttp(host: String, port: Int, user: String, pass: String): Boolean {
        if (!isLoaded) return false
        return startHttpProxy(host, port, user, pass)
    }

    fun stopHttp() {
        if (!isLoaded) return
        stopHttpProxy()
    }

    fun startSocks5(host: String, port: Int, user: String, pass: String): Boolean {
        if (!isLoaded) return false
        return startSocks5Proxy(host, port, user, pass)
    }

    fun stopSocks5() {
        if (!isLoaded) return
        stopSocks5Proxy()
    }

    fun startPac(host: String, port: Int, proxyHost: String, proxyPort: Int, socksPort: Int): Boolean {
        if (!isLoaded) return false
        return startPacServer(host, port, proxyHost, proxyPort, socksPort)
    }

    fun stopPac() {
        if (!isLoaded) return
        stopPacServer()
    }

    fun getStats(): ProxyStats {
        if (!isLoaded) return ProxyStats()
        return try {
            val json = JSONObject(getStatsJson())
            ProxyStats(
                activeConnections = json.optLong("activeConnections", 0),
                bytesUploaded = json.optLong("bytesUploaded", 0),
                bytesDownloaded = json.optLong("bytesDownloaded", 0)
            )
        } catch (e: Exception) {
            ProxyStats()
        }
    }

    fun reset() {
        if (isLoaded) resetStats()
    }

    private external fun startHttpProxy(host: String, port: Int, user: String, pass: String): Boolean
    private external fun stopHttpProxy()

    private external fun startSocks5Proxy(host: String, port: Int, user: String, pass: String): Boolean
    private external fun stopSocks5Proxy()

    private external fun startPacServer(host: String, port: Int, proxyHost: String, proxyPort: Int, socksPort: Int): Boolean
    private external fun stopPacServer()

    private external fun getStatsJson(): String
    private external fun resetStats()
}
