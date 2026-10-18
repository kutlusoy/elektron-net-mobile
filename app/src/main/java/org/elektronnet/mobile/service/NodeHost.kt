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
import org.elektronnet.mobile.wallet.SendEngine
import org.elektronnet.mobile.wallet.WalletReader
import org.elektronnet.mobile.wallet.WalletBootstrap
import org.elektronnet.mobile.wallet.asWalletRpc

object NodeHost {
    @Volatile
    var appVisible: Boolean = false

    var latest: RuntimeSnapshot? by mutableStateOf(null)
        private set

    private var settings: SettingsStore? = null

    val store: SettingsStore? get() = settings
    private var runtime: NodeRuntime? = null
    private var controller: NodeController? = null
    private var datadir: File? = null

    @Synchronized
    fun init(context: Context): SettingsStore {
        settings?.let { return it }
        val app = context.applicationContext
        val store = SettingsStore(app)
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
        return store
    }

    fun tick(): RuntimeSnapshot? {
        val current = runtime ?: return null
        val snapshot = current.tick()
        settings?.saveBudget(current.budget)
        latest = snapshot
        return snapshot
    }

    fun bootstrap(context: Context): WalletBootstrap? {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return null
        val root = datadir ?: return null
        val chain = Chain.fromBuildName(ChainConfig.chain)
        val words = context.applicationContext.assets.open("bip39/english.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        return WalletBootstrap(
            rpc = supervisor.rpc.asWalletRpc(),
            bip39 = Bip39(words),
            testnet = ChainConfig.isTestnet,
            removeWalletFiles = { name -> File(root, "${chain.subdir}/wallets/$name").deleteRecursively() },
        )
    }

    fun receiveWallet(): ReceiveWallet? {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return null
        val store = settings ?: return null
        return ReceiveWallet(
            rpc = supervisor.rpc.asWalletRpc(),
            store = object : AddressStore {
                override var current: String?
                    get() = store.receiveAddress
                    set(value) {
                        store.receiveAddress = value
                    }
            },
        )
    }

    fun walletReader(): WalletReader? {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return null
        return WalletReader(supervisor.rpc.asWalletRpc())
    }

    var incomingUri: String? by mutableStateOf(null)

    fun sendEngine(): SendEngine? {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return null
        return SendEngine(supervisor.rpc.asWalletRpc(), AddressCheck(ChainConfig.isTestnet))
    }

    fun lockWallet() {
        val supervisor = controller?.current?.takeIf { it.state == NodeState.Running } ?: return
        Thread { runCatching { supervisor.rpc.call("walletlock", emptyList<Any?>(), wallet = WalletBootstrap.DEFAULT_WALLET) } }.start()
    }

    val paymentUri: PaymentUri = PaymentUri(ChainConfig.uriScheme, AddressCheck(ChainConfig.isTestnet))

    fun stopNow() {
        controller?.shutdown()
    }

    fun delayFor(snapshot: RuntimeSnapshot): Long = runtime?.suggestedDelayMs(snapshot, appVisible) ?: 30_000
}
