package io.github.aacreat.omniproxy.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import io.github.aacreat.omniproxy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class ProxyTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            OmniProxyService.isRunningState.collectLatest { running ->
                qsTile?.apply {
                    state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    label = getString(R.string.quick_tile_label)
                    icon = Icon.createWithResource(this@ProxyTileService, R.drawable.ic_tile_proxy)
                    updateTile()
                }
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val isRunning = OmniProxyService.isRunningState.value
        if (isRunning) {
            OmniProxyService.stop(this)
        } else {
            OmniProxyService.start(this)
        }
    }
}
