package io.github.aacreat.omniproxy.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.aacreat.omniproxy.core.model.ProxyConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "omniproxy_settings")

class PreferencesManager(private val context: Context) {

    private object Keys {
        val HTTP_ENABLED = booleanPreferencesKey("http_enabled")
        val HTTP_PORT = intPreferencesKey("http_port")
        val SOCKS_ENABLED = booleanPreferencesKey("socks_enabled")
        val SOCKS_PORT = intPreferencesKey("socks_port")
        val PAC_ENABLED = booleanPreferencesKey("pac_enabled")
        val PAC_PORT = intPreferencesKey("pac_port")
        val BIND_ADDRESS = stringPreferencesKey("bind_address")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val WIFI_DIRECT_SSID = stringPreferencesKey("wifi_direct_ssid")
        val WIFI_DIRECT_PASSPHRASE = stringPreferencesKey("wifi_direct_passphrase")
        val WIFI_DIRECT_BAND = intPreferencesKey("wifi_direct_band")
    }

    private val defaultPassphrase: String by lazy { CryptoHelper.generateRandomPassphrase() }

    val configFlow: Flow<ProxyConfig> = context.dataStore.data.map { pref ->
        val storedPassphrase = pref[Keys.WIFI_DIRECT_PASSPHRASE]
        val passphrase = if (storedPassphrase.isNullOrEmpty()) {
            defaultPassphrase
        } else {
            CryptoHelper.decrypt(storedPassphrase).ifEmpty { defaultPassphrase }
        }
        ProxyConfig(
            httpEnabled = pref[Keys.HTTP_ENABLED] ?: false,
            httpPort = pref[Keys.HTTP_PORT] ?: 8080,
            socksEnabled = pref[Keys.SOCKS_ENABLED] ?: false,
            socksPort = pref[Keys.SOCKS_PORT] ?: 1080,
            pacEnabled = pref[Keys.PAC_ENABLED] ?: false,
            pacPort = pref[Keys.PAC_PORT] ?: 8088,
            bindAddress = pref[Keys.BIND_ADDRESS] ?: "0.0.0.0",
            username = CryptoHelper.decrypt(pref[Keys.USERNAME] ?: ""),
            password = CryptoHelper.decrypt(pref[Keys.PASSWORD] ?: ""),
            autoStartOnBoot = pref[Keys.AUTO_START_ON_BOOT] ?: false,
            wifiDirectSsid = pref[Keys.WIFI_DIRECT_SSID] ?: "DIRECT-OP-OmniProxy",
            wifiDirectPassphrase = passphrase,
            wifiDirectBand = pref[Keys.WIFI_DIRECT_BAND] ?: 0
        )
    }

    suspend fun updateHttp(enabled: Boolean, port: Int? = null) {
        context.dataStore.edit { pref ->
            pref[Keys.HTTP_ENABLED] = enabled
            port?.let { pref[Keys.HTTP_PORT] = it }
        }
    }

    suspend fun updateSocks(enabled: Boolean, port: Int? = null) {
        context.dataStore.edit { pref ->
            pref[Keys.SOCKS_ENABLED] = enabled
            port?.let { pref[Keys.SOCKS_PORT] = it }
        }
    }

    suspend fun updatePac(enabled: Boolean, port: Int? = null) {
        context.dataStore.edit { pref ->
            pref[Keys.PAC_ENABLED] = enabled
            port?.let { pref[Keys.PAC_PORT] = it }
        }
    }

    suspend fun updateCredentials(username: String, pass: String) {
        context.dataStore.edit { pref ->
            pref[Keys.USERNAME] = CryptoHelper.encrypt(username)
            pref[Keys.PASSWORD] = CryptoHelper.encrypt(pass)
        }
    }

    suspend fun updateBindAddress(address: String) {
        context.dataStore.edit { pref ->
            pref[Keys.BIND_ADDRESS] = address
        }
    }

    suspend fun updateAutoStart(enabled: Boolean) {
        context.dataStore.edit { pref ->
            pref[Keys.AUTO_START_ON_BOOT] = enabled
        }
    }

    suspend fun updateWifiDirectConfig(ssid: String, passphrase: String, band: Int) {
        context.dataStore.edit { pref ->
            pref[Keys.WIFI_DIRECT_SSID] = ssid
            pref[Keys.WIFI_DIRECT_PASSPHRASE] = CryptoHelper.encrypt(passphrase)
            pref[Keys.WIFI_DIRECT_BAND] = band
        }
    }
}
