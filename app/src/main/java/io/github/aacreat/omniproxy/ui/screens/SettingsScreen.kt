package io.github.aacreat.omniproxy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.aacreat.omniproxy.core.data.PreferencesManager
import io.github.aacreat.omniproxy.core.model.ProxyConfig
import io.github.aacreat.omniproxy.ui.theme.HeroCardShape
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(prefs: PreferencesManager) {
    val scope = rememberCoroutineScope()
    val config by prefs.configFlow.collectAsState(initial = ProxyConfig())

    var userText by remember(config.username) { mutableStateOf(config.username) }
    var passText by remember(config.password) { mutableStateOf(config.password) }
    var directSsidText by remember(config.wifiDirectSsid) { mutableStateOf(config.wifiDirectSsid) }
    var directPassText by remember(config.wifiDirectPassphrase) { mutableStateOf(config.wifiDirectPassphrase) }
    var directSavedHint by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "安全与认证设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = HeroCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "代理访问凭证 (可选)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "若设置，客户端连接 HTTP 或 SOCKS5 时需提供用户名与密码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = userText,
                    onValueChange = { userText = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = passText,
                    onValueChange = { passText = it },
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            prefs.updateCredentials(userText.trim(), passText.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存凭据")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Wi-Fi Direct 直连热点设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = HeroCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "直连热点名称与密码",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "适用于系统隐藏热点入口的 Wi-Fi 平板：通过 WLAN 直连创建热点并共享本机 Wi-Fi 或 VPN。名称若未以 DIRECT-xx 开头将自动补全前缀；密码至少 8 位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = directSsidText,
                    onValueChange = {
                        directSsidText = it
                        directSavedHint = null
                    },
                    label = { Text("热点名称 (SSID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = directPassText,
                    onValueChange = {
                        directPassText = it
                        directSavedHint = null
                    },
                    label = { Text("热点密码 (8-63 位)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (directSavedHint != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = directSavedHint!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val trimmedPass = directPassText.trim()
                        if (trimmedPass.length < 8 || trimmedPass.length > 63) {
                            directSavedHint = "密码长度需在 8 到 63 位之间"
                            return@Button
                        }
                        scope.launch {
                            val normalizedSsid = io.github.aacreat.omniproxy.core.network.WifiDirectManager
                                .normalizeDirectSsid(directSsidText)
                            directSsidText = normalizedSsid
                            prefs.updateWifiDirectConfig(normalizedSsid, trimmedPass)
                            directSavedHint = "已保存，重新开启直连热点后生效"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存直连热点配置")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "系统与服务行为",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = HeroCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "设备开机时自动启动",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "手机重启完成后在后台自动拉起代理服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.autoStartOnBoot,
                        onCheckedChange = { checked ->
                            scope.launch { prefs.updateAutoStart(checked) }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "关于 OmniProxy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = HeroCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "OmniProxy (MD3E Expressive Edition)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "版本: 1.0.0-preview\n技术栈: Kotlin 2.x + Jetpack Compose + Go Core (JNI)\n设计语言: Material Design 3 Expressive (MD3E)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
