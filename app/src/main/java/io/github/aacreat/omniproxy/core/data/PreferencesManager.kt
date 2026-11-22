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

private val Context.dataStore by preferencesDataStore(name = "omni_proxy_settings")

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
    }

    val configFlow: Flow<ProxyConfig> = context.dataStore.data.map { pref ->
        ProxyConfig(
            httpEnabled = pref[Keys.HTTP_ENABLED] ?: false,
            httpPort = pref[Keys.HTTP_PORT] ?: 8080,
            socksEnabled = pref[Keys.SOCKS_ENABLED] ?: false,
            socksPort = pref[Keys.SOCKS_PORT] ?: 1080,
            pacEnabled = pref[Keys.PAC_ENABLED] ?: false,
            pacPort = pref[Keys.PAC_PORT] ?: 8088,
            bindAddress = pref[Keys.BIND_ADDRESS] ?: "0.0.0.0",
            username = pref[Keys.USERNAME] ?: "",
            password = pref[Keys.PASSWORD] ?: "",
            autoStartOnBoot = pref[Keys.AUTO_START_ON_BOOT] ?: false
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
            pref[Keys.USERNAME] = username
            pref[Keys.PASSWORD] = pass
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
}
