package io.github.aacreat.omniproxy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.aacreat.omniproxy.core.data.ProxyRepository
import io.github.aacreat.omniproxy.core.model.HotspotClient
import io.github.aacreat.omniproxy.core.model.NetworkInterfaceInfo
import io.github.aacreat.omniproxy.core.model.ProxyConfig
import io.github.aacreat.omniproxy.core.model.ProxyStats
import io.github.aacreat.omniproxy.core.network.WifiDirectState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared ViewModel backing Dashboard, HotspotManager, and Settings screens.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProxyRepository(application)

    val config: StateFlow<ProxyConfig> = repository.configFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProxyConfig()
    )

    val isRunning: StateFlow<Boolean> = repository.isRunningState
    val stats: StateFlow<ProxyStats> = repository.statsState
    val serviceError: StateFlow<String?> = repository.serviceErrorState
    val wifiDirectState: StateFlow<WifiDirectState> = repository.wifiDirectState

    private val _hotspotClients = MutableStateFlow<List<HotspotClient>>(emptyList())
    val hotspotClients: StateFlow<List<HotspotClient>> = _hotspotClients.asStateFlow()

    fun toggleAllProxies() {
        if (isRunning.value) {
            repository.stopProxyService()
        } else {
            val current = config.value
            if (!current.httpEnabled && !current.socksEnabled && !current.pacEnabled) {
                viewModelScope.launch {
                    repository.updateHttp(true)
                    repository.startProxyService()
                }
            } else {
                repository.startProxyService()
            }
        }
    }

    fun setHttpEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateHttp(enabled)
            if (isRunning.value) repository.startProxyService()
        }
    }

    fun updateHttpPort(port: Int) {
        viewModelScope.launch {
            repository.updateHttp(config.value.httpEnabled, port)
        }
    }

    fun setSocksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSocks(enabled)
            if (isRunning.value) repository.startProxyService()
        }
    }

    fun updateSocksPort(port: Int) {
        viewModelScope.launch {
            repository.updateSocks(config.value.socksEnabled, port)
        }
    }

    fun setPacEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePac(enabled)
            if (isRunning.value) repository.startProxyService()
        }
    }

    fun updatePacPort(port: Int) {
        viewModelScope.launch {
            repository.updatePac(config.value.pacEnabled, port)
        }
    }

    fun saveCredentials(username: String, password: String) {
        viewModelScope.launch {
            repository.updateCredentials(username.trim(), password.trim())
        }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoStart(enabled)
        }
    }

    fun clearServiceError() = repository.clearServiceError()

    fun getRecommendedHost(): String = repository.getRecommendedHost()
    fun getActiveInterfaces(): List<NetworkInterfaceInfo> = repository.getActiveInterfaces()
    fun isVpnConnected(): Boolean = repository.isVpnConnected()

    fun refreshHotspotState() {
        repository.refreshWifiDirectInfo()
        viewModelScope.launch {
            _hotspotClients.value = repository.getHotspotClients()
        }
    }

    fun hasWifiDirectPermission(): Boolean = repository.hasWifiDirectPermission()
    fun requiredWifiDirectPermission(): String = repository.requiredWifiDirectPermission()

    fun startWifiDirectGroup() {
        val cfg = config.value
        repository.startWifiDirectGroup(cfg.wifiDirectSsid, cfg.wifiDirectPassphrase, cfg.wifiDirectBand)
    }

    fun stopWifiDirectGroup() = repository.stopWifiDirectGroup()

    fun saveAndMaybeRestartWifiDirect(ssid: String, passphrase: String, band: Int) {
        viewModelScope.launch {
            repository.updateWifiDirectConfig(ssid, passphrase, band)
            if (wifiDirectState.value.isActive && repository.hasWifiDirectPermission()) {
                repository.restartWifiDirectGroup(ssid, passphrase, band)
            }
        }
    }
}
