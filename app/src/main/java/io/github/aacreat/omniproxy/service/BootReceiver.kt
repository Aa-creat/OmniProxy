package io.github.aacreat.omniproxy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aacreat.omniproxy.core.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver {
    constructor() : super()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesManager(context)
            CoroutineScope(Dispatchers.Default).launch {
                val config = prefs.configFlow.first()
                if (config.autoStartOnBoot) {
                    OmniProxyService.start(context)
                }
            }
        }
    }
}
