package org.elektronnet.mobile.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.elektronnet.mobile.runtime.Headline

class NodeService : Service() {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var next: ScheduledFuture<*>? = null
    private lateinit var settings: SettingsStore
    private lateinit var incoming: IncomingNotifier
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        settings = NodeHost.init(this)
        NodeNotification.createChannel(this)
        incoming = IncomingNotifier(this, settings)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NodeNotification.build(this, NodeHost.latest?.headline, settings.load().userPaused)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NodeNotification.ID, notification, type)
        when (intent?.action) {
            ACTION_STOP -> {
                settings.setUserStopped(true)
                synchronized(this) { next?.cancel(false) }
                worker.execute { shutDown() }
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> settings.setUserPaused(true)
            ACTION_RESUME -> settings.setUserPaused(false)
            else -> {
                settings.setUserStopped(false)
                NodeHost.clearCrash()
            }
        }
        schedule(0)
        return START_STICKY
    }

    @Synchronized
    private fun schedule(delayMs: Long) {
        next?.cancel(false)
        next = worker.schedule({ tickOnce() }, delayMs, TimeUnit.MILLISECONDS)
    }

    // a relay is only reachable while the CPU runs, so on request the service keeps it awake for as long as the relay is up
    private fun syncWakeLock(tor: org.elektronnet.mobile.node.TorSettings, running: Boolean) {
        val wanted = running && tor.enabled && tor.keepAwake && tor.role != org.elektronnet.mobile.node.TorRole.Client
        val held = wakeLock?.isHeld == true
        if (wanted && !held) {
            val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "elektron:relay").apply {
                setReferenceCounted(false)
                acquire()
            }
        } else if (!wanted && held) {
            wakeLock?.release()
        }
    }

    private fun tickOnce() {
        val delay = try {
            val snapshot = NodeHost.tick()
            if (snapshot == null) {
                stopSelf()
                return
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val state = settings.load()
            syncWakeLock(state.network.tor, snapshot.headline !is Headline.Stopped)
            manager.notify(NodeNotification.ID, NodeNotification.build(this, snapshot.headline, state.userPaused, snapshot.peerNetworks))
            runCatching {
                val wallet = NodeHost.activeWallet
                NodeHost.walletReader()?.transactions(INCOMING_WINDOW)?.let { incoming.check(wallet, it) }
            }
            if (snapshot.headline is Headline.Stopped && state.userStopped) {
                stopSelf()
                return
            }
            NodeHost.delayFor(snapshot)
        } catch (e: Exception) {
            RETRY_AFTER_ERROR_MS
        }
        schedule(delay)
    }

    private fun shutDown() {
        NodeHost.tick()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        wakeLock?.takeIf { it.isHeld }?.release()
        Thread { NodeHost.stopNow() }.start()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "org.elektronnet.mobile.action.STOP"
        const val ACTION_PAUSE = "org.elektronnet.mobile.action.PAUSE"
        const val ACTION_RESUME = "org.elektronnet.mobile.action.RESUME"
        private const val RETRY_AFTER_ERROR_MS = 30_000L
        private const val INCOMING_WINDOW = 25

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, NodeService::class.java))
        }

        fun send(context: Context, action: String) {
            ContextCompat.startForegroundService(context, Intent(context, NodeService::class.java).setAction(action))
        }
    }
}
