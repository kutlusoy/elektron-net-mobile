package org.elektronnet.mobile.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.BuildConfig
import org.elektronnet.mobile.node.ReleaseKind
import org.elektronnet.mobile.node.UpdateCheck
import org.elektronnet.mobile.node.UpdateFetch
import org.elektronnet.mobile.node.UpdateResult
import org.elektronnet.mobile.service.NodeHost

enum class UpdateStatus { Idle, Checking, Failed, UpToDate }

object UpdateState {
    // the debug and nightly builds carry the public test key, only the release build the release key
    val testBuild: Boolean = BuildConfig.BUILD_TYPE != "release"

    var auto by mutableStateOf(true)
        private set
    var status by mutableStateOf(UpdateStatus.Idle)
        private set
    var offer: UpdateResult.Newer? by mutableStateOf(null)
        private set
    var dismissed by mutableStateOf("")
        private set

    val visibleOffer: UpdateResult.Newer? get() = offer?.takeIf { it.tag != dismissed }

    fun load() {
        val store = NodeHost.store ?: return
        auto = store.updateAuto
        dismissed = store.updateDismissed
        offer = decode(store.updateOffer)?.takeIf { stillNewer(it) }
    }

    fun setAuto(on: Boolean) {
        auto = on
        NodeHost.store?.updateAuto = on
    }

    fun dismiss() {
        val tag = offer?.tag ?: return
        dismissed = tag
        NodeHost.store?.updateDismissed = tag
    }

    // runs when the app is open: nothing happens unless the automatic check is on and a day has passed
    suspend fun checkIfDue() {
        val store = NodeHost.store ?: return
        if (!auto || status == UpdateStatus.Checking) return
        if (!UpdateCheck.due(store.updateLastOkMs, store.updateLastAttemptMs, System.currentTimeMillis())) return
        check(manual = false)
    }

    // the automatic check stays silent unless a newer release exists, only a check by hand reports "up to date" or a failure
    suspend fun check(manual: Boolean = true) {
        val store = NodeHost.store
        if (status == UpdateStatus.Checking) return
        status = UpdateStatus.Checking
        store?.updateLastAttemptMs = System.currentTimeMillis()
        val json = withContext(Dispatchers.IO) { runCatching { UpdateFetch.releases() }.getOrNull() }
        val result = json?.let { UpdateCheck.evaluate(BuildConfig.VERSION_NAME, BuildConfig.MOBILE_VERSION, it, testBuild) }
        when (result) {
            null, UpdateResult.Unknown -> status = if (manual) UpdateStatus.Failed else UpdateStatus.Idle
            UpdateResult.UpToDate -> {
                status = if (manual) UpdateStatus.UpToDate else UpdateStatus.Idle
                offer = null
                store?.updateOffer = ""
                store?.updateLastOkMs = System.currentTimeMillis()
            }
            is UpdateResult.Newer -> {
                status = UpdateStatus.Idle
                offer = result
                store?.updateOffer = "${result.kind.name}|${result.tag}"
                store?.updateLastOkMs = System.currentTimeMillis()
            }
        }
    }

    private fun decode(text: String): UpdateResult.Newer? {
        val kind = ReleaseKind.entries.firstOrNull { it.name == text.substringBefore('|') } ?: return null
        val tag = text.substringAfter('|', "").ifEmpty { return null }
        return UpdateResult.Newer(tag, kind)
    }

    // an offer saved by an older version of the app is dropped once the app has caught up
    private fun stillNewer(saved: UpdateResult.Newer): Boolean {
        val current = UpdateCheck.current(BuildConfig.VERSION_NAME, BuildConfig.MOBILE_VERSION) ?: return false
        val offered = org.elektronnet.mobile.node.AppVersion.parse(saved.tag) ?: return false
        return offered > current
    }
}
