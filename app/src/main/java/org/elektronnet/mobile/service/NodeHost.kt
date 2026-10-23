package org.elektronnet.mobile.service

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NetworkServices
import org.elektronnet.mobile.node.NodeController
import org.elektronnet.mobile.node.ServiceBinaries
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.policy.PolicyEngine
import org.elektronnet.mobile.runtime.NodeRuntime
import org.elektronnet.mobile.runtime.RuntimeSnapshot
import org.elektronnet.mobile.wallet.AddressCheck
import org.elektronnet.mobile.wallet.AddressStore
import org.elektronnet.mobile.wallet.Bip39
import org.elektronnet.mobile.wallet.PaymentUri
import org.elektronnet.mobile.wallet.ReceiveWallet
import org.elektronnet.mobile.wallet.EscrowManager
import org.elektronnet.mobile.wallet.MessageTools
import org.elektronnet.mobile.wallet.PsbtTools
import org.elektronnet.mobile.wallet.SetupRequest
import org.elektronnet.mobile.wallet.SetupStatus
import org.elektronnet.mobile.wallet.WalletSetup
import org.elektronnet.mobile.wallet.SendEngine
import org.elektronnet.mobile.wallet.SweepEngine
import org.elektronnet.mobile.wallet.WalletReader
import org.elektronnet.mobile.wallet.CoinControl
import org.elektronnet.mobile.wallet.OwnWallet
import org.elektronnet.mobile.wallet.WalletBootstrap
import org.elektronnet.mobile.wallet.WalletRegistry
import org.elektronnet.mobile.wallet.asWalletRpc

object NodeHost {
    private const val SCAN_TIMEOUT_MS = 900_000

    @Volatile
    var appVisible: Boolean = false

    var latest: RuntimeSnapshot? by mutableStateOf(null)
        private set

    private var settings: SettingsStore? = null

    val store: SettingsStore? get() = settings
    private var runtime: NodeRuntime? = null
    private var controller: NodeController? = null
    var networkServices: NetworkServices? = null
        private set

    var networkPorts: org.elektronnet.mobile.node.NetworkPorts = ChainConfig.networkPorts
        private set
    private var datadir: File? = null
    private var appContext: Context? = null

    private val walletSetup = WalletSetup(onNameTaken = { old -> renameForSetup(old) }) { name -> appContext?.let { bootstrap(it, name) } }

    private var registry: WalletRegistry? = null

    var walletFlag: Boolean by mutableStateOf(false)
        private set

    private fun markWalletReady(value: Boolean) {
        walletFlag = value
        settings?.setWalletReady(value)
    }

    var activeWallet: String by mutableStateOf(WalletBootstrap.DEFAULT_WALLET)
        private set

    var wallets: List<OwnWallet> by mutableStateOf(emptyList())
        private set

    private fun refreshWallets() {
        val reg = registry ?: return
        wallets = reg.wallets()
        activeWallet = reg.active().name
    }

    // The wallet name is already used on the node: give the new wallet the next free name and keep its entry in the list
    private fun renameForSetup(old: String): String? {
        val reg = registry ?: return null
        val onDisk = runningSupervisor()?.let { org.elektronnet.mobile.wallet.WalletReconciler(it.rpc.asWalletRpc(), reg).namesOnDisk() } ?: return null
        walletNamesOnDisk = onDisk.toSet()
        val next = reg.nextName(walletNamesOnDisk + old)
        reg.changeName(old, next)
        refreshWallets()
        return next
    }

    // The "Reload wallets" button: lists the wallets the node has but the list lacks, drops entries without a wallet and loads the rest
    fun reloadWallets(): org.elektronnet.mobile.wallet.WalletReconciler.Result? {
        val supervisor = runningSupervisor() ?: return null
        val reg = registry ?: return null
        val rpc = supervisor.rpc.asWalletRpc()
        val busy = if (setupStatus != SetupStatus.None) setOf(walletSetup.walletName) else emptySet()
        val reconciler = org.elektronnet.mobile.wallet.WalletReconciler(rpc, reg) { busy }
        val result = reconciler.reconcile(adopt = true) ?: return null
        walletNamesOnDisk = reconciler.namesOnDisk().orEmpty().toSet()
        if (reg.wallets().isNotEmpty()) markWalletReady(true)
        refreshWallets()
        return result
    }

    // Lets the next tick compare the wallet list with the node again and load wallets that are listed but not loaded
    fun requestReconcile() {
        reconciledFor = 0L
    }

    fun switchWallet(name: String) {
        registry?.setActive(name)
        refreshWallets()
        requestReconcile()
    }

    fun renameWallet(name: String, label: String) {
        registry?.rename(name, label)
        refreshWallets()
    }

    fun walletIsUnused(name: String): Boolean {
        val supervisor = runningSupervisor() ?: return false
        return org.elektronnet.mobile.wallet.WalletRemover(supervisor.rpc.asWalletRpc()) {}.isUnused(name)
    }

    fun removeWallet(name: String, passphrase: String?): org.elektronnet.mobile.wallet.RemoveFailure? {
        val supervisor = runningSupervisor() ?: return org.elektronnet.mobile.wallet.RemoveFailure.NodeNotReady
        val root = datadir ?: return org.elektronnet.mobile.wallet.RemoveFailure.Unknown
        val chain = Chain.fromBuildName(ChainConfig.chain)
        val failure = org.elektronnet.mobile.wallet.WalletRemover(supervisor.rpc.asWalletRpc()) { wallet ->
            org.elektronnet.mobile.wallet.deleteWalletDir(File(root, "${chain.subdir}/wallets/$wallet"))
        }.remove(name, passphrase)
        if (failure != null) return failure
        registry?.remove(name)
        settings?.setReceiveAddressFor(name, null)
        refreshWallets()
        if (wallets.isEmpty()) {
            walletSetup.cancel()
            setupStatus = walletSetup.status
            markWalletReady(false)
            settings?.rescanPending = false
        }
        return null
    }

    fun rescanActiveWallet(passphrase: String): org.elektronnet.mobile.wallet.RescanOutcome {
        val context = appContext ?: return org.elektronnet.mobile.wallet.RescanOutcome.Failed(org.elektronnet.mobile.wallet.BootstrapFailure.NodeNotReady)
        val node = bootstrap(context, activeWallet) ?: return org.elektronnet.mobile.wallet.RescanOutcome.Failed(org.elektronnet.mobile.wallet.BootstrapFailure.NodeNotReady)
        return node.rescan(passphrase)
    }

    fun walletNames(): List<String> = (registry?.wallets().orEmpty().map { it.name } + WalletBootstrap.DEFAULT_WALLET).distinct()

    @Volatile
    private var walletNamesOnDisk: Set<String> = emptySet()

    // Reads the cached list, the screens call this on the main thread
    fun nextWalletName(): String = registry?.nextName(walletNamesOnDisk) ?: WalletBootstrap.DEFAULT_WALLET

    private fun liveWalletName(): String {
        val reg = registry ?: return WalletBootstrap.DEFAULT_WALLET
        val onDisk = runningSupervisor()?.let { org.elektronnet.mobile.wallet.WalletReconciler(it.rpc.asWalletRpc(), reg).namesOnDisk() }
        if (onDisk != null) walletNamesOnDisk = onDisk.toSet()
        return reg.nextName(walletNamesOnDisk)
    }

    var setupStatus: SetupStatus by mutableStateOf(SetupStatus.None)
        private set

    @Synchronized
    fun init(context: Context): SettingsStore {
        settings?.let { return it }
        val app = context.applicationContext
        appContext = app
        val store = SettingsStore(app)
        val reg = WalletRegistry(store.walletRegistryStore())
        walletFlag = store.walletReady()
        if (store.walletReady()) reg.ensureDefault()
        registry = reg
        refreshWallets()
        if (store.walletReady() && store.rescanPending) {
            walletSetup.markRescanPending(store.rescanWallet)
            setupStatus = walletSetup.status
        }
        networkPorts = ChainConfig.networkPorts.resolved()
        val datadir = File(app.filesDir, ChainConfig.datadirName)
        datadir.mkdirs()
        this.datadir = datadir
        val config = NodeConfig(
            binary = File(app.applicationInfo.nativeLibraryDir, "libelektrond.so"),
            datadir = datadir,
            chain = Chain.fromBuildName(ChainConfig.chain),
            rpcPort = ChainConfig.rpcPort,
            p2pPort = ChainConfig.p2pPort,
            fastprune = store.fastprune(),
            homeDir = app.filesDir,
            network = store.loadNetwork(),
            ports = networkPorts,
        )
        val device = AndroidDeviceState(app, datadir)
        val services = NetworkServices(
            binaries = ServiceBinaries(File(app.applicationInfo.nativeLibraryDir, "libtor.so"), File(app.applicationInfo.nativeLibraryDir, "libi2pd.so")),
            root = File(app.filesDir, "network"),
            ports = networkPorts,
            dnsServers = { AndroidDeviceState.dnsServers(app) },
            prepareI2pData = { dir -> installI2pData(app, dir) },
        )
        networkServices = services
        val nodeController = NodeController(config, services = services)
        controller = nodeController
        runtime = NodeRuntime(
            controller = nodeController,
            engine = PolicyEngine(),
            settings = { store.load() },
            device = { device.read(appVisible) },
            budget = store.loadBudget(),
        )
        settings = store
        org.elektronnet.mobile.ui.theme.UnitState.current = store.amountUnit
        org.elektronnet.mobile.ui.theme.FiatState.load(store.fiat)
        org.elektronnet.mobile.wallet.Amount.unitProvider = { org.elektronnet.mobile.ui.theme.UnitState.current }
        org.elektronnet.mobile.ui.theme.UiState.settings = store.loadUi(org.elektronnet.mobile.ui.theme.Palettes.all.map { it.key }.toSet())
        return store
    }

    fun tick(): RuntimeSnapshot? {
        val current = runtime ?: return null
        val snapshot = current.tick()
        settings?.saveBudget(current.budget)
        latest = snapshot
        maintainWallets(snapshot)
        return snapshot
    }

    @Volatile
    private var reconciledFor = 0L

    @Volatile
    private var maintenanceRunning = false

    private val pendingCoinRefresh = java.util.Collections.synchronizedSet(linkedSetOf<String>())

    var restoreNotice: String? by mutableStateOf(null)

    private fun maintainWallets(snapshot: RuntimeSnapshot) {
        val supervisor = controller?.current ?: return
        if (supervisor.state != NodeState.Running || maintenanceRunning) return
        val synced = snapshot.headline is org.elektronnet.mobile.runtime.Headline.Synchronized
        val reconcileNeeded = reconciledFor != supervisor.startedAt
        val refreshNeeded = synced && pendingCoinRefresh.isNotEmpty()
        if (!reconcileNeeded && !refreshNeeded) return
        maintenanceRunning = true
        Thread {
            try {
                val reg = registry
                val rpc = supervisor.rpc.asWalletRpc()
                if (reconcileNeeded && reg != null) {
                    val busy = if (setupStatus != SetupStatus.None) setOf(walletSetup.walletName) else emptySet()
                    val result = org.elektronnet.mobile.wallet.WalletReconciler(rpc, reg) { busy }.reconcile(adopt = false)
                    if (result != null) {
                        walletNamesOnDisk = org.elektronnet.mobile.wallet.WalletReconciler(rpc, reg).namesOnDisk().orEmpty().toSet()
                        reconciledFor = supervisor.startedAt
                        pendingCoinRefresh.addAll(reg.wallets().map { it.name })
                        if (reg.wallets().isNotEmpty()) markWalletReady(true)
                        refreshWallets()
                    }
                }
                if (synced) {
                    val refresh = org.elektronnet.mobile.wallet.CoinRefresh(supervisor.rpc.asWalletRpc(SCAN_TIMEOUT_MS))
                    pendingCoinRefresh.toList().forEach { name ->
                        refresh.refresh(name)
                        pendingCoinRefresh.remove(name)
                    }
                }
            } finally {
                maintenanceRunning = false
            }
        }.start()
    }

    fun beginSetup(request: SetupRequest, label: String = "") {
        registry?.add(request.walletName, label)
        walletSetup.begin(request)
        setupStatus = walletSetup.status
    }

    fun retrySetup() {
        walletSetup.retry()
        setupStatus = walletSetup.status
    }

    fun cancelSetup() {
        val name = walletSetup.walletName
        val before = walletSetup.status
        walletSetup.cancel()
        if (before == SetupStatus.Waiting || before is SetupStatus.Failed) {
            val onNode = runningSupervisor()?.let { org.elektronnet.mobile.wallet.WalletRemover(it.rpc.asWalletRpc()) {}.existsOnNode(name) }
            if (onNode != true && registry?.wallets()?.size.let { it != null && it > 0 }) {
                registry?.remove(name)
                refreshWallets()
            }
            requestReconcile()
        }
        settings?.rescanPending = false
        setupStatus = walletSetup.status
    }

    fun provideRescanPassphrase(passphrase: String) {
        walletSetup.resumeRescan(passphrase)
        setupStatus = walletSetup.status
    }

    val needsRescanPassphrase: Boolean get() = setupStatus == SetupStatus.NeedsRescan && !walletSetup.hasRescanPassphrase

    fun setupTick() {
        val synced = latest?.headline is org.elektronnet.mobile.runtime.Headline.Synchronized
        val status = walletSetup.step(synced)
        val store = settings
        when (status) {
            is SetupStatus.Ready -> {
                markWalletReady(true)
                store?.rescanPending = false
                registry?.setActive(walletSetup.walletName)
                refreshWallets()
            }
            SetupStatus.NeedsRescan, SetupStatus.Rescanning -> {
                markWalletReady(true)
                store?.rescanPending = true
                store?.rescanWallet = walletSetup.walletName
                registry?.setActive(walletSetup.walletName)
                refreshWallets()
            }
            else -> Unit
        }
        setupStatus = status
    }

    fun bootstrap(context: Context, walletName: String = WalletBootstrap.DEFAULT_WALLET): WalletBootstrap? {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return null
        val root = datadir ?: return null
        val chain = Chain.fromBuildName(ChainConfig.chain)
        val words = context.applicationContext.assets.open("bip39/english.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        return WalletBootstrap(
            rpc = supervisor.rpc.asWalletRpc(),
            bip39 = Bip39(words),
            testnet = ChainConfig.isTestnet,
            removeWalletFiles = { name -> org.elektronnet.mobile.wallet.deleteWalletDir(File(root, "${chain.subdir}/wallets/$name")) },
            walletName = walletName,
        )
    }

    fun receiveWallet(): ReceiveWallet? {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return null
        val store = settings ?: return null
        val wallet = activeWallet
        return ReceiveWallet(
            rpc = supervisor.rpc.asWalletRpc(),
            store = object : AddressStore {
                override var current: String?
                    get() = store.receiveAddressFor(wallet)
                    set(value) {
                        store.setReceiveAddressFor(wallet, value)
                    }
            },
            walletName = wallet,
        )
    }

    fun walletReader(): WalletReader? {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return null
        return WalletReader(supervisor.rpc.asWalletRpc(), activeWallet)
    }

    var incomingUri: String? by mutableStateOf(null)

    private fun runningSupervisor() = controller?.current?.takeIf { it.state == NodeState.Running }

    private val addressCheck: AddressCheck get() = AddressCheck(ChainConfig.isTestnet)

    private var cachedFees: Pair<Long, org.elektronnet.mobile.explorer.RecommendedFees>? = null

    fun explorerFees(): org.elektronnet.mobile.explorer.RecommendedFees? {
        val urls = settings?.explorers.orEmpty()
        if (urls.isEmpty()) return null
        val now = System.currentTimeMillis()
        cachedFees?.let { if (now - it.first < FEE_CACHE_MS) return it.second }
        val fresh = org.elektronnet.mobile.explorer.ExplorerClient().fees(urls) ?: return null
        cachedFees = now to fresh
        return fresh
    }

    fun explorerTxUrl(txid: String): String? = settings?.explorers?.firstOrNull()?.let { org.elektronnet.mobile.explorer.ExplorerList.txUrl(it, txid) }

    fun sendEngine(wallet: String = activeWallet): SendEngine? =
        runningSupervisor()?.let { SendEngine(it.rpc.asWalletRpc(), addressCheck, wallet, ::explorerFees, { store?.spendReused != false }) }

    fun escrowManager(): EscrowManager? {
        val supervisor = runningSupervisor() ?: return null
        val store = settings ?: return null
        val root = datadir ?: return null
        val chain = Chain.fromBuildName(ChainConfig.chain)
        return EscrowManager(
            rpc = supervisor.rpc.asWalletRpc(),
            store = store.escrowStore(),
            testnet = ChainConfig.isTestnet,
            removeWalletFiles = { name -> org.elektronnet.mobile.wallet.deleteWalletDir(File(root, "${chain.subdir}/wallets/$name")) },
        )
    }

    fun psbtTools(): PsbtTools? {
        val supervisor = runningSupervisor() ?: return null
        val store = settings ?: return null
        return PsbtTools(supervisor.rpc.asWalletRpc()) {
            walletNames() + store.escrowStore().load().filter { it.phase == org.elektronnet.mobile.wallet.EscrowPhase.Active }.map { it.walletName }
        }
    }

    fun sweepEngine(): SweepEngine? =
        runningSupervisor()?.let { SweepEngine(it.rpc.asWalletRpc(timeoutMs = SCAN_TIMEOUT_MS), ChainConfig.isTestnet, addressCheck) }

    fun messageTools(): MessageTools? = runningSupervisor()?.let { MessageTools(it.rpc.asWalletRpc(), addressCheck, activeWallet) }

    fun consoleRunner(): org.elektronnet.mobile.console.ConsoleRunner? {
        val supervisor = runningSupervisor() ?: return null
        val context = appContext ?: return null
        val floor = settings?.load()?.storageFloorBytes ?: 0L
        return ConsoleStore.runner(context, supervisor.rpc, floor)
    }

    fun consoleRpc(): org.elektronnet.mobile.wallet.WalletRpc? = runningSupervisor()?.rpc?.asWalletRpc(timeoutMs = 120_000)

    fun feeBumper(): org.elektronnet.mobile.wallet.FeeBumper? = runningSupervisor()?.let { org.elektronnet.mobile.wallet.FeeBumper(it.rpc.asWalletRpc(), activeWallet) }

    fun cpfp(): org.elektronnet.mobile.wallet.Cpfp? = runningSupervisor()?.let { org.elektronnet.mobile.wallet.Cpfp(it.rpc.asWalletRpc(), activeWallet) }

    fun walletExport(): org.elektronnet.mobile.wallet.WalletExport? = runningSupervisor()?.let { org.elektronnet.mobile.wallet.WalletExport(it.rpc.asWalletRpc(), activeWallet) }

    private fun walletRestore(): org.elektronnet.mobile.wallet.WalletRestore? {
        val supervisor = runningSupervisor() ?: return null
        val root = datadir ?: return null
        val chain = Chain.fromBuildName(ChainConfig.chain)
        return org.elektronnet.mobile.wallet.WalletRestore(supervisor.rpc.asWalletRpc()) { name ->
            org.elektronnet.mobile.wallet.deleteWalletDir(File(root, "${chain.subdir}/wallets/$name"))
        }
    }

    private fun registerRestored(name: String) {
        registry?.add(name, "")
        registry?.setActive(name)
        markWalletReady(true)
        pendingCoinRefresh.add(name)
        restoreNotice = name
        refreshWallets()
    }

    fun restoreWalletFile(file: File): org.elektronnet.mobile.wallet.RestoreResult {
        val restore = walletRestore() ?: return org.elektronnet.mobile.wallet.RestoreResult.Failed(org.elektronnet.mobile.wallet.RestoreFailure.NodeNotReady)
        val result = restore.restore(file, liveWalletName())
        if (result is org.elektronnet.mobile.wallet.RestoreResult.Restored && result.encrypted) registerRestored(result.walletName)
        return result
    }

    fun protectRestoredWallet(name: String, passphrase: String): org.elektronnet.mobile.wallet.RestoreFailure? {
        val restore = walletRestore() ?: return org.elektronnet.mobile.wallet.RestoreFailure.NodeNotReady
        val failure = restore.protect(name, passphrase)
        if (failure == null) registerRestored(name)
        return failure
    }

    fun discardRestoredWallet(name: String) {
        walletRestore()?.discard(name)
    }

    fun addressBook(): org.elektronnet.mobile.wallet.AddressBook? = appContext?.let { org.elektronnet.mobile.wallet.AddressBook(File(it.filesDir, "contacts.json"), addressCheck) }

    fun coinControl(wallet: String = activeWallet): CoinControl? = runningSupervisor()?.let { CoinControl(it.rpc.asWalletRpc(), wallet) }

    fun lockWallet() {
        val supervisor = runningSupervisor() ?: return
        val names = walletNames() + (settings?.escrowStore()?.load().orEmpty()).map { it.walletName }
        Thread { names.forEach { runCatching { supervisor.rpc.call("walletlock", emptyList<Any?>(), wallet = it) } } }.start()
    }

    private const val FEE_CACHE_MS = 60_000L

    val paymentUri: PaymentUri = PaymentUri(ChainConfig.uriScheme, AddressCheck(ChainConfig.isTestnet))

    data class PeerRow(val peer: org.elektronnet.mobile.node.PeerInfo, val country: org.elektronnet.mobile.geo.Country?)

    private var geoIp: org.elektronnet.mobile.geo.GeoIp? = null
    val geoAvailable: Boolean get() = geoIp?.available == true

    private fun geo(): org.elektronnet.mobile.geo.GeoIp {
        geoIp?.let { return it }
        val reader = runCatching {
            val app = appContext ?: return@runCatching null
            val file = File(app.filesDir, "geoip/dbip-country-lite.mmdb")
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                app.assets.open("geoip/dbip-country-lite.mmdb").use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            java.io.RandomAccessFile(file, "r").use { raf ->
                org.elektronnet.mobile.geo.MmdbReader(raf.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, raf.length()))
            }
        }.getOrNull()
        return org.elektronnet.mobile.geo.GeoIp(reader).also { geoIp = it }
    }

    enum class PeerResult { Added, Saved, Exists, Invalid }

    var peerResult: PeerResult? by mutableStateOf(null)
    var peerVersion by mutableStateOf(0)

    // call from a background thread, it talks to the node
    fun addPeer(text: String): PeerResult {
        val arg = org.elektronnet.mobile.node.PeerAddress.parse(text)
        val store = store
        val result = when {
            arg == null || store == null -> PeerResult.Invalid
            arg in store.manualPeers -> PeerResult.Exists
            else -> {
                store.manualPeers = store.manualPeers + arg
                val running = controller?.current?.takeIf { it.state == NodeState.Running }
                if (running != null && runCatching { running.rpc.call("addnode", listOf(arg, "add")) }.isSuccess) PeerResult.Added else PeerResult.Saved
            }
        }
        peerResult = result
        peerVersion++
        return result
    }

    fun removePeer(arg: String) {
        val store = store ?: return
        store.manualPeers = store.manualPeers - arg
        controller?.current?.takeIf { it.state == NodeState.Running }?.let { runCatching { it.rpc.call("addnode", listOf(arg, "remove")) } }
        peerResult = null
        peerVersion++
    }

    fun telemetry(): org.elektronnet.mobile.node.Telemetry? {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return null
        return runCatching { supervisor.telemetry() }.getOrNull()
    }

    fun peers(): List<PeerRow>? {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return null
        val geo = geo()
        return runCatching { supervisor.peers().map { PeerRow(it, geo.lookup(it.address)) } }.getOrNull()
    }

    data class Diagnostics(val startedAt: Long, val warmup: String?, val lastLogAt: Long, val rpcProblem: String?)

    fun diagnostics(): Diagnostics? {
        val supervisor = controller?.current ?: return null
        return Diagnostics(supervisor.startedAt, supervisor.warmup, supervisor.output.lastAddedAt, supervisor.rpcProblem)
    }

    fun logTail(count: Int): List<String> = controller?.current?.output?.tail(count).orEmpty()

    fun clearCrash() {
        controller?.clearCrash()
    }

    var addressNotice: org.elektronnet.mobile.node.AddressKind? by mutableStateOf(null)

    var addressNothingToRenew: org.elektronnet.mobile.node.AddressKind? by mutableStateOf(null)

    fun renewAddress(kind: org.elektronnet.mobile.node.AddressKind) {
        val root = datadir ?: return
        val chain = Chain.fromBuildName(ChainConfig.chain)
        val keys = org.elektronnet.mobile.node.AddressKeys(File(root, chain.subdir))
        addressNotice = null
        addressNothingToRenew = null
        if (!keys.exists(kind)) {
            addressNothingToRenew = kind
            return
        }
        Thread {
            controller?.restartNode()
            if (keys.renew(kind)) addressNotice = kind
        }.start()
    }

    fun resetChainData(): Boolean {
        val dir = datadir ?: return false
        controller?.shutdown()
        return runCatching { org.elektronnet.mobile.node.DatadirReset.perform(dir) }.isSuccess
    }

    fun stopNow() {
        controller?.shutdown()
    }

    fun delayFor(snapshot: RuntimeSnapshot): Long = runtime?.suggestedDelayMs(snapshot, appVisible) ?: 30_000

    private fun installI2pData(context: Context, dir: File) {
        val target = File(dir, "certificates")
        if (target.isDirectory) return
        copyAssetTree(context, "i2pd/certificates", target)
    }

    private fun copyAssetTree(context: Context, asset: String, target: File) {
        val children = context.assets.list(asset).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            runCatching { context.assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } } }
            return
        }
        target.mkdirs()
        children.forEach { copyAssetTree(context, "$asset/$it", File(target, it)) }
    }
}
