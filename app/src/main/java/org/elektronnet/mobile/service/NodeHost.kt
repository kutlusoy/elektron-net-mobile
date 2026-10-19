package org.elektronnet.mobile.service

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeController
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
    private var datadir: File? = null
    private var appContext: Context? = null

    private val walletSetup = WalletSetup { name -> appContext?.let { bootstrap(it, name) } }

    private var registry: WalletRegistry? = null

    var activeWallet: String by mutableStateOf(WalletBootstrap.DEFAULT_WALLET)
        private set

    var wallets: List<OwnWallet> by mutableStateOf(emptyList())
        private set

    private fun refreshWallets() {
        val reg = registry ?: return
        wallets = reg.wallets()
        activeWallet = reg.active().name
    }

    fun switchWallet(name: String) {
        registry?.setActive(name)
        refreshWallets()
    }

    fun renameWallet(name: String, label: String) {
        registry?.rename(name, label)
        refreshWallets()
    }

    fun walletNames(): List<String> = (registry?.wallets().orEmpty().map { it.name } + WalletBootstrap.DEFAULT_WALLET).distinct()

    fun nextWalletName(): String = registry?.nextName() ?: WalletBootstrap.DEFAULT_WALLET

    var setupStatus: SetupStatus by mutableStateOf(SetupStatus.None)
        private set

    @Synchronized
    fun init(context: Context): SettingsStore {
        settings?.let { return it }
        val app = context.applicationContext
        appContext = app
        val store = SettingsStore(app)
        val reg = WalletRegistry(store.walletRegistryStore())
        if (store.walletReady()) reg.ensureDefault()
        registry = reg
        refreshWallets()
        if (store.walletReady() && store.rescanPending) {
            walletSetup.markRescanPending(store.rescanWallet)
            setupStatus = walletSetup.status
        }
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
        )
        val device = AndroidDeviceState(app, datadir)
        val nodeController = NodeController(config)
        controller = nodeController
        runtime = NodeRuntime(
            controller = nodeController,
            engine = PolicyEngine(),
            settings = { store.load() },
            device = { device.read(appVisible) },
            budget = store.loadBudget(),
        )
        settings = store
        org.elektronnet.mobile.ui.theme.UiState.settings = store.loadUi(org.elektronnet.mobile.ui.theme.Palettes.all.map { it.key }.toSet())
        return store
    }

    fun tick(): RuntimeSnapshot? {
        val current = runtime ?: return null
        val snapshot = current.tick()
        settings?.saveBudget(current.budget)
        latest = snapshot
        return snapshot
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
        if (name != WalletBootstrap.DEFAULT_WALLET && (before == SetupStatus.Waiting || before is SetupStatus.Failed)) {
            registry?.remove(name)
            refreshWallets()
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
                store?.setWalletReady(true)
                store?.rescanPending = false
                registry?.setActive(walletSetup.walletName)
                refreshWallets()
            }
            SetupStatus.NeedsRescan, SetupStatus.Rescanning -> {
                store?.setWalletReady(true)
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
            removeWalletFiles = { name -> File(root, "${chain.subdir}/wallets/$name").deleteRecursively() },
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

    fun sendEngine(wallet: String = activeWallet): SendEngine? =
        runningSupervisor()?.let { SendEngine(it.rpc.asWalletRpc(), addressCheck, wallet) }

    fun escrowManager(): EscrowManager? {
        val supervisor = runningSupervisor() ?: return null
        val store = settings ?: return null
        val root = datadir ?: return null
        val chain = Chain.fromBuildName(ChainConfig.chain)
        return EscrowManager(
            rpc = supervisor.rpc.asWalletRpc(),
            store = store.escrowStore(),
            testnet = ChainConfig.isTestnet,
            removeWalletFiles = { name -> File(root, "${chain.subdir}/wallets/$name").deleteRecursively() },
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

    fun coinControl(wallet: String = activeWallet): CoinControl? = runningSupervisor()?.let { CoinControl(it.rpc.asWalletRpc(), wallet) }

    fun lockWallet() {
        val supervisor = runningSupervisor() ?: return
        val names = walletNames() + (settings?.escrowStore()?.load().orEmpty()).map { it.walletName }
        Thread { names.forEach { runCatching { supervisor.rpc.call("walletlock", emptyList<Any?>(), wallet = it) } } }.start()
    }

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

    fun stopNow() {
        controller?.shutdown()
    }

    fun delayFor(snapshot: RuntimeSnapshot): Long = runtime?.suggestedDelayMs(snapshot, appVisible) ?: 30_000
}
