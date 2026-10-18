package org.elektronnet.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import org.elektronnet.mobile.wallet.SetupStatus
import org.elektronnet.mobile.ui.model.HomeUiModel
import org.elektronnet.mobile.ui.model.NodeTabModel
import org.elektronnet.mobile.ui.node.NodeScreen
import org.elektronnet.mobile.ui.receive.ReceiveScreen
import org.elektronnet.mobile.ui.send.SendScreen
import org.elektronnet.mobile.ui.tools.ToolsScreen
import org.elektronnet.mobile.ui.wallet.TxDetailsScreen
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import org.elektronnet.mobile.runtime.Headline
import org.elektronnet.mobile.ui.model.HistoryModel
import org.elektronnet.mobile.wallet.Balances
import org.elektronnet.mobile.wallet.TxItem
import org.elektronnet.mobile.ui.settings.SettingsScreen
import org.elektronnet.mobile.ui.wallet.WalletScreen

private data class WalletData(val balances: Balances, val items: List<TxItem>)

private enum class Tab(val label: Int, val icon: ImageVector) {
    Wallet(R.string.tab_wallet, Icons.Filled.Home),
    Node(R.string.tab_node, Icons.Filled.Refresh),
    Settings(R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun AppRoot(onStart: () -> Unit, onStop: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Wallet.ordinal) }
    var showSoon by remember { mutableStateOf(false) }
    var showReceive by rememberSaveable { mutableStateOf(false) }
    var showSend by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var openTx by rememberSaveable { mutableStateOf<String?>(null) }
    var walletData by remember { mutableStateOf<WalletData?>(null) }
    val store = NodeHost.store
    var settings by remember { mutableStateOf(store?.load() ?: PolicySettings()) }
    val snapshot = NodeHost.latest
    var storeReady by remember { mutableStateOf(store?.walletReady() ?: false) }
    val setup = NodeHost.setupStatus
    val walletReady = storeReady || setup != SetupStatus.None

    LaunchedEffect(setup) {
        if (setup is SetupStatus.Ready || setup == SetupStatus.NeedsRescan || setup == SetupStatus.Rescanning) storeReady = true
        while (setup == SetupStatus.Waiting || setup == SetupStatus.Rescanning || (setup == SetupStatus.NeedsRescan && !NodeHost.needsRescanPassphrase)) {
            withContext(Dispatchers.IO) { NodeHost.setupTick() }
            delay(1_000)
        }
    }

    LaunchedEffect(walletReady, refreshTick) {
        while (walletReady) {
            if (NodeHost.appVisible) {
                val fresh = withContext(Dispatchers.IO) {
                    val reader = NodeHost.walletReader() ?: return@withContext null
                    runCatching { WalletData(reader.balances(), reader.transactions()) }.getOrNull()
                }
                if (fresh != null) walletData = fresh
            }
            delay(if (NodeHost.latest?.headline is Headline.Synchronized) 8_000 else 15_000)
        }
    }

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
                    onFinished = {},
                )
                return@Box
            }
            val detail = openTx?.let { id -> walletData?.items?.firstOrNull { it.txid == id } }
            if (detail != null) {
                TxDetailsScreen(detail, onClose = { openTx = null })
                return@Box
            }
            val incoming = NodeHost.incomingUri
            if (incoming != null && walletReady) showSend = true
            if (showTools) {
                ToolsScreen(onClose = { showTools = false })
                return@Box
            }
            if (showSend) {
                SendScreen(
                    initialUri = incoming,
                    onClose = {
                        NodeHost.incomingUri = null
                        showSend = false
                    },
                    onDone = { id ->
                        NodeHost.incomingUri = null
                        showSend = false
                        refreshTick++
                        openTx = id
                    },
                )
                return@Box
            }
            if (showReceive) {
                ReceiveScreen(onClose = { showReceive = false })
                return@Box
            }
            when (Tab.entries[tab]) {
                Tab.Wallet -> Column {
                    SetupBanner(
                        status = setup,
                        needsPassphrase = NodeHost.needsRescanPassphrase,
                        onRetry = NodeHost::retrySetup,
                        onStartOver = NodeHost::cancelSetup,
                        onPassphrase = NodeHost::provideRescanPassphrase,
                    )
                    WalletScreen(
                    model = HomeUiModel.from(snapshot?.headline),
                    balance = HistoryModel.balance(
                        walletData?.balances,
                        synced = snapshot?.headline is Headline.Synchronized,
                        hasHistory = walletData?.items?.isNotEmpty() == true,
                    ),
                    days = HistoryModel.days(walletData?.items.orEmpty(), ZoneId.systemDefault()),
                    onOpenTx = { openTx = it },
                    onOpenTools = { showTools = true },
                    onOpenNode = { tab = Tab.Node.ordinal },
                    onReceive = { showReceive = true },
                    onSend = { showSend = true },
                )
                }
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
