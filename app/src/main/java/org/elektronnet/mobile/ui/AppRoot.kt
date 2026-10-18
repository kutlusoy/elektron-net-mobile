package org.elektronnet.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.policy.PolicySettings
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.firstrun.FirstRunScreen
import org.elektronnet.mobile.ui.model.HomeUiModel
import org.elektronnet.mobile.ui.model.NodeTabModel
import org.elektronnet.mobile.ui.node.NodeScreen
import org.elektronnet.mobile.ui.settings.SettingsScreen
import org.elektronnet.mobile.ui.wallet.WalletScreen

private enum class Tab(val label: Int, val icon: ImageVector) {
    Wallet(R.string.tab_wallet, Icons.Filled.Home),
    Node(R.string.tab_node, Icons.Filled.Refresh),
    Settings(R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun AppRoot(onStart: () -> Unit, onStop: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Wallet.ordinal) }
    var showSoon by remember { mutableStateOf(false) }
    val store = NodeHost.store
    var settings by remember { mutableStateOf(store?.load() ?: PolicySettings()) }
    val snapshot = NodeHost.latest
    var walletReady by remember { mutableStateOf(store?.walletReady() ?: false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { if (ChainConfig.isTestnet) TestnetBar() },
        bottomBar = {
            if (walletReady) NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry.ordinal,
                        onClick = { tab = entry.ordinal },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (!walletReady) {
                FirstRunScreen(
                    onAllowMetered = {
                        store?.setAllowMetered(it)
                        settings = settings.copy(allowMetered = it)
                    },
                    onFinished = {
                        store?.setWalletReady(true)
                        walletReady = true
                    },
                )
                return@Box
            }
            when (Tab.entries[tab]) {
                Tab.Wallet -> WalletScreen(
                    model = HomeUiModel.from(snapshot?.headline),
                    balance = null,
                    onOpenNode = { tab = Tab.Node.ordinal },
                    onReceive = { showSoon = true },
                    onSend = { showSoon = true },
                )
                Tab.Node -> NodeScreen(
                    model = NodeTabModel.from(snapshot, settings.userPaused),
                    onPause = {
                        onPause()
                        settings = settings.copy(userPaused = true)
                    },
                    onResume = {
                        onResume()
                        settings = settings.copy(userPaused = false)
                    },
                    onStop = onStop,
                    onStart = onStart,
                )
                Tab.Settings -> SettingsScreen(
                    settings = settings,
                    onAllowMetered = {
                        store?.setAllowMetered(it)
                        settings = settings.copy(allowMetered = it)
                    },
                    onChargingOnly = {
                        store?.setChargingOnly(it)
                        settings = settings.copy(chargingOnly = it)
                    },
                    onBackground = {
                        store?.setBackgroundEnabled(it)
                        settings = settings.copy(backgroundEnabled = it)
                    },
                )
            }
        }
    }

    if (showSoon) {
        AlertDialog(
            onDismissRequest = { showSoon = false },
            confirmButton = { TextButton(onClick = { showSoon = false }) { Text(stringResource(R.string.action_ok)) } },
            text = { Text(stringResource(R.string.feature_soon)) },
        )
    }
}
