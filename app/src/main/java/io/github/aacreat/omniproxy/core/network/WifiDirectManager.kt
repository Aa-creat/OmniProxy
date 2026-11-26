package io.github.aacreat.omniproxy.core.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiDirectState(
    val isSupported: Boolean = true,
    val isActive: Boolean = false,
    val isBusy: Boolean = false,
    val ssid: String = "",
    val passphrase: String = "",
    val interfaceName: String = "",
    val gatewayIp: String = "192.168.49.1",
    val clientCount: Int = 0,
    val errorMessage: String? = null
)

class WifiDirectManager(context: Context) {

    private val appContext = context.applicationContext
    private val wifiP2pManager: WifiP2pManager? =
        appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val _state = MutableStateFlow(
        WifiDirectState(isSupported = wifiP2pManager != null)
    )
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    init {
        if (wifiP2pManager != null) {
            channel = wifiP2pManager.initialize(appContext, Looper.getMainLooper(), null)
            refreshGroupInfo()
        }
    }

    fun hasRequiredPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun requiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
    }

    @SuppressLint("MissingPermission")
    fun startGroup(customSsid: String, customPassphrase: String) {
        val manager = wifiP2pManager
        val ch = channel
        if (manager == null || ch == null) {
            _state.value = _state.value.copy(
                isSupported = false,
                errorMessage = "当前设备不支持 Wi-Fi Direct (WLAN 直连)"
            )
            return
        }
        if (!hasRequiredPermission()) {
            _state.value = _state.value.copy(
                errorMessage = "缺少附近设备 / 定位权限，请先授权后再开启"
            )
            return
        }

        _state.value = _state.value.copy(isBusy = true, errorMessage = null)

        val actionListener = object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                refreshGroupInfo()
            }

            override fun onFailure(reason: Int) {
                val msg = when (reason) {
                    WifiP2pManager.BUSY -> "Wi-Fi 系统正忙，请稍后重试（或先关闭其他直连会话）"
                    WifiP2pManager.P2P_UNSUPPORTED -> "当前设备不支持 Wi-Fi Direct"
                    else -> "启动失败 (错误码: $reason)，请确认设备已开启 Wi-Fi"
                }
                Log.w("WifiDirectManager", "createGroup failed: $reason")
                _state.value = _state.value.copy(
                    isBusy = false,
                    errorMessage = msg
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val normalizedSsid = normalizeDirectSsid(customSsid)
            val normalizedPass = if (customPassphrase.length in 8..63) customPassphrase else "12345678"
            try {
                val config = WifiP2pConfig.Builder()
                    .setNetworkName(normalizedSsid)
                    .setPassphrase(normalizedPass)
                    .build()
                manager.createGroup(ch, config, actionListener)
            } catch (e: Exception) {
                Log.w("WifiDirectManager", "createGroup with config failed, falling back: ${e.message}")
                manager.createGroup(ch, actionListener)
            }
        } else {
            manager.createGroup(ch, actionListener)
        }
    }

    fun stopGroup() {
        val manager = wifiP2pManager ?: return
        val ch = channel ?: return
        _state.value = _state.value.copy(isBusy = true, errorMessage = null)
        manager.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _state.value = WifiDirectState(isSupported = true, isActive = false, isBusy = false)
            }

            override fun onFailure(reason: Int) {
                _state.value = _state.value.copy(
                    isBusy = false,
                    errorMessage = "关闭直连热点失败 (错误码: $reason)"
                )
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun refreshGroupInfo() {
        val manager = wifiP2pManager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermission()) return

        manager.requestGroupInfo(ch) { group ->
            if (group != null && group.isGroupOwner) {
                manager.requestConnectionInfo(ch) { connInfo ->
                    val gateway = connInfo?.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                    _state.value = _state.value.copy(
                        isSupported = true,
                        isActive = true,
                        isBusy = false,
                        ssid = group.networkName.orEmpty(),
                        passphrase = group.passphrase.orEmpty(),
                        interfaceName = group.`interface`.orEmpty(),
                        gatewayIp = gateway,
                        clientCount = group.clientList?.size ?: 0,
                        errorMessage = null
                    )
                }
            } else {
                _state.value = _state.value.copy(
                    isSupported = true,
                    isActive = false,
                    isBusy = false
                )
            }
        }
    }

    companion object {
        private val DIRECT_PREFIX_REGEX = Regex("^DIRECT-[A-Za-z0-9]{2}.*")

        fun normalizeDirectSsid(raw: String): String {
            val trimmed = raw.trim().ifEmpty { "OmniProxy" }
            return if (DIRECT_PREFIX_REGEX.matches(trimmed)) {
                trimmed.take(32)
            } else {
                "DIRECT-OP-$trimmed".take(32)
            }
        }
    }
}
