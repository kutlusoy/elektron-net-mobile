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
import org.elektronnet.mobile.policy.PolicyEngine
import org.elektronnet.mobile.runtime.NodeRuntime
import org.elektronnet.mobile.runtime.RuntimeSnapshot

object NodeHost {
    @Volatile
    var appVisible: Boolean = false

    var latest: RuntimeSnapshot? by mutableStateOf(null)
        private set

    private var settings: SettingsStore? = null

    val store: SettingsStore? get() = settings
    private var runtime: NodeRuntime? = null
    private var controller: NodeController? = null

    @Synchronized
    fun init(context: Context): SettingsStore {
        settings?.let { return it }
        val app = context.applicationContext
        val store = SettingsStore(app)
        val datadir = File(app.filesDir, ChainConfig.datadirName)
        datadir.mkdirs()
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

    fun stopNow() {
        controller?.shutdown()
    }

    fun delayFor(snapshot: RuntimeSnapshot): Long = runtime?.suggestedDelayMs(snapshot, appVisible) ?: 30_000
}
