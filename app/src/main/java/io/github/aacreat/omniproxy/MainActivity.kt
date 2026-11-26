package io.github.aacreat.omniproxy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Badge
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.aacreat.omniproxy.ui.MainViewModel
import io.github.aacreat.omniproxy.ui.screens.DashboardScreen
import io.github.aacreat.omniproxy.ui.screens.HotspotManagerScreen
import io.github.aacreat.omniproxy.ui.screens.SettingsScreen
import io.github.aacreat.omniproxy.ui.theme.AccentOnline
import io.github.aacreat.omniproxy.ui.theme.OmniProxyTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            OmniProxyTheme {
                val mainViewModel: MainViewModel = viewModel()
                MainScreen(viewModel = mainViewModel)
            }
        }
    }
}

enum class NavItem(val label: String, val icon: ImageVector) {
    DASHBOARD("控制台", Icons.Default.Dashboard),
    HOTSPOT("热点管理", Icons.Default.WifiTethering),
    SETTINGS("设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val isRunning by viewModel.isRunning.collectAsState()
    val serviceError by viewModel.serviceError.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(serviceError) {
        serviceError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearServiceError()
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "OmniProxy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 16.dp)) {
                        Badge(
                            containerColor = if (isRunning) AccentOnline else MaterialTheme.colorScheme.outline
                        ) {
                            Text(if (isRunning) "运行中" else "已停止")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavItem.values().forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        label = { Text(item.label) },
                        icon = {
                            Icon(imageVector = item.icon, contentDescription = item.label)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedIndex) {
                0 -> DashboardScreen(viewModel = viewModel)
                1 -> HotspotManagerScreen(viewModel = viewModel)
                2 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
