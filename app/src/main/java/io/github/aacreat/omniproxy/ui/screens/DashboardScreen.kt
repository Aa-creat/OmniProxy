package io.github.aacreat.omniproxy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aacreat.omniproxy.service.OmniProxyService
import io.github.aacreat.omniproxy.ui.MainViewModel
import io.github.aacreat.omniproxy.ui.components.ExpressiveProxyCard
import io.github.aacreat.omniproxy.ui.components.NetworkBanner
import io.github.aacreat.omniproxy.ui.components.QrCodeDialog
import io.github.aacreat.omniproxy.ui.theme.HeroCardShape

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val config by viewModel.config.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val wifiDirectState by viewModel.wifiDirectState.collectAsState()

    var qrCodeContent by remember { mutableStateOf<String?>(null) }

    val recommendedHost = remember(wifiDirectState.isActive, wifiDirectState.gatewayIp, isRunning) {
        if (wifiDirectState.isActive && wifiDirectState.gatewayIp.isNotEmpty()) {
            wifiDirectState.gatewayIp
        } else {
            viewModel.getRecommendedHost()
        }
    }
    val isHotspotActive = remember(wifiDirectState.isActive, isRunning) {
        wifiDirectState.isActive || viewModel.getActiveInterfaces().any { it.isHotspot }
    }
    val isVpnActive = remember(isRunning) { viewModel.isVpnConnected() }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(bottom = 80.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        NetworkBanner(
            hostIp = recommendedHost,
            isHotspotActive = isHotspotActive,
            isVpnActive = isVpnActive,
            onShowQrCode = { qrCodeContent = it }
        )

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Button(
                onClick = { viewModel.toggleAllProxies() },
                shape = HeroCardShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "停止全部代理服务" else "启动代理服务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ExpressiveProxyCard(
            title = "HTTP / HTTPS 代理",
            subtitle = "支持 CONNECT 隧道与网页请求转发",
            port = config.httpPort,
            enabled = config.httpEnabled,
            icon = Icons.Default.Http,
            onToggle = { enabled -> viewModel.setHttpEnabled(enabled) },
            onPortChange = { port -> viewModel.updateHttpPort(port) }
        )

        ExpressiveProxyCard(
            title = "SOCKS5 代理",
            subtitle = "支持 TCP 传输与通用网络代理协议",
            port = config.socksPort,
            enabled = config.socksEnabled,
            icon = Icons.Default.Router,
            onToggle = { enabled -> viewModel.setSocksEnabled(enabled) },
            onPortChange = { port -> viewModel.updateSocksPort(port) }
        )

        ExpressiveProxyCard(
            title = "PAC 自动代理服务",
            subtitle = "http://" + recommendedHost + ":" + config.pacPort + "/proxy.pac",
            port = config.pacPort,
            enabled = config.pacEnabled,
            icon = Icons.Default.Language,
            onToggle = { enabled -> viewModel.setPacEnabled(enabled) },
            onPortChange = { port -> viewModel.updatePacPort(port) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = HeroCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Text(
                    text = "实时流量与连接",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricItem("活跃连接", stats.activeConnections.toString() + " 个")
                    MetricItem("已上传", OmniProxyService.formatBytes(stats.bytesUploaded))
                    MetricItem("已下载", OmniProxyService.formatBytes(stats.bytesDownloaded))
                }
            }
        }
    }

    qrCodeContent?.let { content ->
        QrCodeDialog(
            title = "扫描配置代理",
            content = content,
            onDismiss = { qrCodeContent = null }
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
