package io.github.aacreat.omniproxy.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import io.github.aacreat.omniproxy.core.model.HotspotClient
import io.github.aacreat.omniproxy.core.model.NetworkInterfaceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkManager(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun getActiveInterfaces(): List<NetworkInterfaceInfo> {
        val list = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val name = intf.name
                        val isHotspot = name.contains("ap", ignoreCase = true) ||
                                name.contains("swlan", ignoreCase = true) ||
                                name.contains("rndis", ignoreCase = true) ||
                                name.contains("p2p", ignoreCase = true)
                        val isVpn = name.contains("tun", ignoreCase = true) ||
                                name.contains("ppp", ignoreCase = true)
                        val isWifi = name.contains("wlan", ignoreCase = true) && !isHotspot

                        val displayName = when {
                            isHotspot -> "热点网卡 ($name)"
                            isVpn -> "VPN 隧道 ($name)"
                            isWifi -> "Wi-Fi 局域网 ($name)"
                            else -> "网络接口 ($name)"
                        }

                        list.add(
                            NetworkInterfaceInfo(
                                name = name,
                                displayName = displayName,
                                ipAddress = addr.hostAddress ?: "",
                                isHotspot = isHotspot,
                                isVpn = isVpn,
                                isWifi = isWifi
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error querying interfaces: ${e.message}")
        }
        return list
    }

    fun getRecommendedHost(): String {
        val interfaces = getActiveInterfaces()
        val hotspot = interfaces.firstOrNull { it.isHotspot }
        if (hotspot != null) return hotspot.ipAddress

        val wifi = interfaces.firstOrNull { it.isWifi }
        if (wifi != null) return wifi.ipAddress

        val nonVpn = interfaces.firstOrNull { !it.isVpn }
        if (nonVpn != null) return nonVpn.ipAddress

        return interfaces.firstOrNull()?.ipAddress ?: "127.0.0.1"
    }

    fun isVpnConnected(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getHotspotClients(): List<HotspotClient> = withContext(Dispatchers.IO) {
        val clients = mutableListOf<HotspotClient>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line: String?
                reader.readLine()
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.trim().split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val flags = parts[2]
                        val mac = parts[3]
                        if (flags != "0x0" && mac != "00:00:00:00:00:00") {
                            clients.add(
                                HotspotClient(
                                    ip = ip,
                                    mac = mac.uppercase(),
                                    deviceName = "客户端 (${ip.substringAfterLast('.')})"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("NetworkManager", "Unable to read /proc/net/arp: ${e.message}")
        }
        clients
    }
}
