package io.github.aacreat.omniproxy.core.data

import android.content.Context
import io.github.aacreat.omniproxy.core.model.HotspotClient
import io.github.aacreat.omniproxy.core.model.NetworkInterfaceInfo
import io.github.aacreat.omniproxy.core.model.ProxyConfig
import io.github.aacreat.omniproxy.core.model.ProxyStats
import io.github.aacreat.omniproxy.core.network.NetworkManager
import io.github.aacreat.omniproxy.core.network.WifiDirectManager
import io.github.aacreat.omniproxy.core.network.WifiDirectState
import io.github.aacreat.omniproxy.service.OmniProxyService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for proxy configuration, service lifecycle, and network/hotspot state.
 */
class ProxyRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = PreferencesManager(appContext)
    private val networkManager = NetworkManager(appContext)
    private val wifiDirectManager = WifiDirectManager(appContext)

    val configFlow: Flow<ProxyConfig> = prefs.configFlow
    val isRunningState: StateFlow<Boolean> = OmniProxyService.isRunningState
    val statsState: StateFlow<ProxyStats> = OmniProxyService.statsState
    val serviceErrorState: StateFlow<String?> = OmniProxyService.errorState
    val wifiDirectState: StateFlow<WifiDirectState> = wifiDirectManager.state

    suspend fun updateHttp(enabled: Boolean, port: Int? = null) = prefs.updateHttp(enabled, port)
    suspend fun updateSocks(enabled: Boolean, port: Int? = null) = prefs.updateSocks(enabled, port)
    suspend fun updatePac(enabled: Boolean, port: Int? = null) = prefs.updatePac(enabled, port)
    suspend fun updateCredentials(username: String, pass: String) = prefs.updateCredentials(username, pass)
    suspend fun updateAutoStart(enabled: Boolean) = prefs.updateAutoStart(enabled)
    suspend fun updateWifiDirectConfig(ssid: String, passphrase: String, band: Int) =
        prefs.updateWifiDirectConfig(ssid, passphrase, band)

    fun startProxyService() = OmniProxyService.start(appContext)
    fun stopProxyService() = OmniProxyService.stop(appContext)
    fun clearServiceError() = OmniProxyService.clearError()

    fun getRecommendedHost(): String = networkManager.getRecommendedHost()
    fun getActiveInterfaces(): List<NetworkInterfaceInfo> = networkManager.getActiveInterfaces()
    fun isVpnConnected(): Boolean = networkManager.isVpnConnected()
    suspend fun getHotspotClients(): List<HotspotClient> = networkManager.getHotspotClients()

    fun hasWifiDirectPermission(): Boolean = wifiDirectManager.hasRequiredPermission()
    fun requiredWifiDirectPermission(): String = wifiDirectManager.requiredPermission()
    fun refreshWifiDirectInfo() = wifiDirectManager.refreshGroupInfo()
    fun startWifiDirectGroup(ssid: String, passphrase: String, band: Int) =
        wifiDirectManager.startGroup(ssid, passphrase, band)
    fun restartWifiDirectGroup(ssid: String, passphrase: String, band: Int) =
        wifiDirectManager.restartGroup(ssid, passphrase, band)
    fun stopWifiDirectGroup() = wifiDirectManager.stopGroup()
}
