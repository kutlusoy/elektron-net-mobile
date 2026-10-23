package org.elektronnet.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.policy.PolicySettings
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.firstrun.FirstRunScreen
import org.elektronnet.mobile.ui.theme.UiState
import org.elektronnet.mobile.wallet.SetupStatus
import org.elektronnet.mobile.ui.model.HomeUiModel
import org.elektronnet.mobile.ui.model.NodeTabModel
import org.elektronnet.mobile.ui.node.NodeScreen
import org.elektronnet.mobile.ui.receive.ReceiveScreen
import org.elektronnet.mobile.ui.send.SendScreen
import org.elektronnet.mobile.ui.console.ConsoleRoot
import org.elektronnet.mobile.ui.tools.ToolsPage
import org.elektronnet.mobile.ui.tools.ToolsScreen
import org.elektronnet.mobile.ui.wallets.CoinsScreen
import org.elektronnet.mobile.ui.wallets.ContactsScreen
import org.elektronnet.mobile.ui.wallets.WalletDetailsScreen
import org.elektronnet.mobile.ui.wallets.RestoreNotice
import org.elektronnet.mobile.ui.wallets.WalletSwitcher
import org.elektronnet.mobile.ui.wallets.WalletsScreen
import org.elektronnet.mobile.ui.wallet.TxDetailsScreen
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import org.elektronnet.mobile.runtime.Headline
import org.elektronnet.mobile.ui.model.HistoryModel
import org.elektronnet.mobile.wallet.Balances
import org.elektronnet.mobile.wallet.TxItem
import org.elektronnet.mobile.ui.settings.SettingsScreen
import org.elektronnet.mobile.ui.wallet.HistoryScreen
import org.elektronnet.mobile.ui.wallet.NoWalletCard
import org.elektronnet.mobile.ui.wallet.WalletScreen

private data class WalletData(val balances: Balances, val items: List<TxItem>)

private enum class Overlay { None, Tools, Messages, Sweep, Send, Receive, Details, Coins, Wallets, AddWallet, Console, Contacts, PeerScan }

private const val RECENT_COUNT = 8

private enum class Tab(val label: Int, val icon: ImageVector) {
    Wallet(R.string.tab_wallet, Icons.Filled.Home),
    History(R.string.tab_history, Icons.AutoMirrored.Filled.List),
    Node(R.string.tab_node, Icons.Filled.Refresh),
    Settings(R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun AppRoot(onStart: () -> Unit, onStop: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onBiometric: (Boolean) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Wallet.ordinal) }
    var showSoon by remember { mutableStateOf(false) }
    var overlayName by rememberSaveable { mutableStateOf(Overlay.None.name) }
    val overlay = runCatching { Overlay.valueOf(overlayName) }.getOrDefault(Overlay.None)
    fun show(next: Overlay) {
        overlayName = next.name
    }
    var refreshTick by remember { mutableStateOf(0) }
    var explorers by remember { mutableStateOf(NodeHost.store?.explorers ?: emptyList()) }
    var fastprune by remember { mutableStateOf(NodeHost.store?.fastprune() ?: true) }
    var openTx by rememberSaveable { mutableStateOf<String?>(null) }
    var walletData by remember { mutableStateOf<WalletData?>(null) }
    var histories by remember { mutableStateOf<Map<String, List<org.elektronnet.mobile.wallet.TxItem>>>(emptyMap()) }
    val store = NodeHost.store
    var settings by remember { mutableStateOf(store?.load() ?: PolicySettings()) }
    val snapshot = NodeHost.latest
    var storeReady by remember { mutableStateOf(store?.walletReady() ?: false) }
    val setup = NodeHost.setupStatus
    val walletReady = ((storeReady || NodeHost.walletFlag) && NodeHost.wallets.isNotEmpty()) || setup != SetupStatus.None
    var nodeOnly by remember { mutableStateOf(store?.nodeOnly == true) }
    val shell = walletReady || nodeOnly

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val fiatSettings = org.elektronnet.mobile.ui.theme.FiatState.settings
    LaunchedEffect(fiatSettings) {
        if (fiatSettings.enabled) delay(FIAT_DEBOUNCE_MS)
        var lastFetch = 0L
        while (fiatSettings.enabled) {
            val now = System.currentTimeMillis()
            if (NodeHost.appVisible && now - lastFetch >= org.elektronnet.mobile.fiat.FiatRates.POLL_MS) {
                org.elektronnet.mobile.ui.theme.FiatState.refresh()
                lastFetch = now
            }
            delay(FIAT_TICK_MS)
        }
    }

    LaunchedEffect(setup) {
        if (setup is SetupStatus.Ready || setup == SetupStatus.NeedsRescan || setup == SetupStatus.Rescanning) storeReady = true
        while (setup == SetupStatus.Waiting || setup == SetupStatus.Rescanning || (setup == SetupStatus.NeedsRescan && !NodeHost.needsRescanPassphrase)) {
            withContext(Dispatchers.IO) { NodeHost.setupTick() }
            delay(1_000)
        }
    }

    LaunchedEffect(NodeHost.activeWallet) { walletData = null }
    LaunchedEffect(walletReady, refreshTick, NodeHost.activeWallet) {
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

    LaunchedEffect(tab, walletReady, NodeHost.activeWallet, refreshTick) {
        while (walletReady && tab == Tab.History.ordinal) {
            val name = NodeHost.activeWallet
            if (NodeHost.appVisible) {
                val all = withContext(Dispatchers.IO) { runCatching { NodeHost.walletReader()?.allTransactions() }.getOrNull() }
                if (all != null) histories = histories + (name to all)
            }
            delay(10_000)
        }
    }

    var peers by remember { mutableStateOf<List<NodeHost.PeerRow>>(emptyList()) }
    var telemetry by remember { mutableStateOf<org.elektronnet.mobile.node.Telemetry?>(null) }
    LaunchedEffect(tab, shell) {
        while (shell && tab == Tab.Node.ordinal) {
            if (NodeHost.appVisible) {
                val fresh = withContext(Dispatchers.IO) { NodeHost.peers() }
                if (fresh != null) peers = fresh
                val stats = withContext(Dispatchers.IO) { NodeHost.telemetry() }
                if (stats != null) telemetry = stats
            }
            delay(5_000)
        }
    }

    BrandBackground {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            Column(modifier = if (ChainConfig.isTestnet) Modifier else Modifier.statusBarsPadding()) {
                if (ChainConfig.isTestnet) TestnetBar()
                if (org.elektronnet.mobile.ui.ScreenshotMode.allowed) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(R.string.screenshot_banner),
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error).padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
                if (org.elektronnet.mobile.service.ConsoleState.expertActive) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(if (org.elektronnet.mobile.service.ConsoleState.dangerActive) R.string.expert_banner_danger else R.string.expert_banner),
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (org.elektronnet.mobile.service.ConsoleState.dangerActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
            }
        },
        bottomBar = {
            if (shell) PillNavBar(
                items = Tab.entries.map { Triple(stringResource(it.label), it.icon, it.ordinal) },
                selected = tab,
                onSelect = {
                    tab = it
                    overlayName = Overlay.None.name
                    openTx = null
                    NodeHost.incomingUri = null
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (!shell) {
                FirstRunScreen(
                    onAllowMetered = {
                        store?.setAllowMetered(it)
                        settings = settings.copy(allowMetered = it)
                    },
                    onFinished = {},
                    onSkip = {
                        store?.nodeOnly = true
                        nodeOnly = true
                        onStart()
                    },
                )
                return@Box
            }
            val detail = openTx?.let { id -> histories[NodeHost.activeWallet]?.firstOrNull { it.txid == id } ?: walletData?.items?.firstOrNull { it.txid == id } }
            if (detail != null) {
                TxDetailsScreen(detail, onClose = { openTx = null }, onBumped = { refreshTick++ })
                return@Box
            }
            val incoming = NodeHost.incomingUri
            if (incoming != null && walletReady) show(Overlay.Send)
            when (overlay) {
                Overlay.Tools -> ToolsScreen(onClose = { show(Overlay.None) })
                Overlay.Messages -> ToolsScreen(onClose = { show(Overlay.None) }, start = ToolsPage.Messages)
                Overlay.Sweep -> ToolsScreen(onClose = { show(Overlay.None) }, start = ToolsPage.Sweep)
                Overlay.Send -> SendScreen(
                    initialUri = incoming,
                    onClose = {
                        NodeHost.incomingUri = null
                        show(Overlay.None)
                    },
                    onDone = { id ->
                        NodeHost.incomingUri = null
                        show(Overlay.None)
                        refreshTick++
                        openTx = id
                    },
                )
                Overlay.Receive -> ReceiveScreen(onClose = { show(Overlay.None) })
                Overlay.Details -> WalletDetailsScreen(onBack = { show(Overlay.None) })
                Overlay.Coins -> CoinsScreen(onBack = { show(Overlay.None) })
                Overlay.Wallets -> WalletsScreen(onBack = { show(Overlay.None) }, onAdd = { show(Overlay.AddWallet) })
                Overlay.AddWallet -> FirstRunScreen(
                    onAllowMetered = {},
                    onFinished = { show(Overlay.None) },
                    addWallet = true,
                    onCancel = { show(if (walletReady) Overlay.Wallets else Overlay.None) },
                )
                Overlay.Console -> ConsoleRoot(onClose = { show(Overlay.None) })
                Overlay.Contacts -> ContactsScreen(onBack = { show(Overlay.None) })
                Overlay.PeerScan -> org.elektronnet.mobile.ui.scan.QrScannerScreen(
                    onResult = { text ->
                        show(Overlay.None)
                        scope.launch(Dispatchers.IO) { NodeHost.addPeer(text) }
                    },
                    onClose = { show(Overlay.None) },
                )
                Overlay.None -> Unit
            }
            if (overlay != Overlay.None) return@Box
            Column {
            BrandHeader(compact = true, showTagline = true)
            Box(modifier = Modifier.weight(1f)) {
            when (Tab.entries[tab]) {
                Tab.Wallet -> if (!walletReady) NoWalletCard(onCreate = { show(Overlay.AddWallet) }) else Column {
                    WalletSwitcher(
                        onDetails = { show(Overlay.Details) },
                        onCoins = { show(Overlay.Coins) },
                        onMessages = { show(Overlay.Messages) },
                        onSweep = { show(Overlay.Sweep) },
                        onTools = { show(Overlay.Tools) },
                        onWallets = { show(Overlay.Wallets) },
                        onContacts = { show(Overlay.Contacts) },
                    )
                    NodeHost.restoreNotice?.let { RestoreNotice { NodeHost.restoreNotice = null } }
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
                    days = HistoryModel.days(walletData?.items.orEmpty().take(RECENT_COUNT), ZoneId.systemDefault()),
                    hasMore = (walletData?.items?.size ?: 0) > RECENT_COUNT,
                    onShowAll = { tab = Tab.History.ordinal },
                    onOpenTx = { openTx = it },
                    onOpenTools = { show(Overlay.Tools) },
                    onOpenNode = { tab = Tab.Node.ordinal },
                    onReceive = { show(Overlay.Receive) },
                    onSend = { show(Overlay.Send) },
                )
                }
                Tab.History -> if (!walletReady) NoWalletCard(onCreate = { show(Overlay.AddWallet) }) else HistoryScreen(
                    wallet = NodeHost.wallets.firstOrNull { it.name == NodeHost.activeWallet },
                    items = histories[NodeHost.activeWallet],
                    onOpenTx = { openTx = it },
                )
                Tab.Node -> androidx.compose.runtime.CompositionLocalProvider(org.elektronnet.mobile.ui.node.LocalPeerScan provides { show(Overlay.PeerScan) }) { NodeScreen(
                    model = NodeTabModel.from(snapshot, settings.userPaused),
                    peers = peers,
                    telemetry = telemetry,
                    showGeoCredit = NodeHost.geoAvailable,
                    onOpenConsole = { show(Overlay.Console) },
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
                ) }
                Tab.Settings -> SettingsScreen(
                    settings = settings,
                    onPolicy = {
                        store?.savePolicy(it)
                        settings = it
                    },
                    explorers = explorers,
                    onExplorers = {
                        store?.explorers = it
                        explorers = it
                    },
                    fastprune = fastprune,
                    onFastprune = {
                        store?.setFastprune(it)
                        fastprune = it
                    },
                    biometric = org.elektronnet.mobile.ui.security.AppLockState.enabled,
                    onBiometric = onBiometric,
                    ui = UiState.settings,
                    onUi = {
                        store?.saveUi(it)
                        UiState.settings = it
                    },
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

private const val FIAT_DEBOUNCE_MS = 2_000L
private const val FIAT_TICK_MS = 15_000L
