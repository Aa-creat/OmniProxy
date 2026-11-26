package io.github.aacreat.omniproxy.core.model

data class ProxyConfig(
    val httpEnabled: Boolean = false,
    val httpPort: Int = 8080,
    val socksEnabled: Boolean = false,
    val socksPort: Int = 1080,
    val pacEnabled: Boolean = false,
    val pacPort: Int = 8088,
    val bindAddress: String = "0.0.0.0",
    val username: String = "",
    val password: String = "",
    val autoStartOnBoot: Boolean = false,
    val wifiDirectSsid: String = "DIRECT-OP-OmniProxy",
    val wifiDirectPassphrase: String = "12345678",
    val wifiDirectBand: Int = 0
)

data class ProxyStats(
    val activeConnections: Long = 0,
    val bytesUploaded: Long = 0,
    val bytesDownloaded: Long = 0
)

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val ipAddress: String,
    val isHotspot: Boolean = false,
    val isVpn: Boolean = false,
    val isWifi: Boolean = false
)

data class HotspotClient(
    val ip: String,
    val mac: String,
    val deviceName: String = "",
    val isReachable: Boolean = true
)
