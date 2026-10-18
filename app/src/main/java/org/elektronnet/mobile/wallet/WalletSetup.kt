package org.elektronnet.mobile.wallet

class SetupRequest(val phrase: List<String>, val passphrase: String, val restore: Boolean)

sealed interface SetupStatus {
    data object None : SetupStatus
    data object Waiting : SetupStatus
    data class Failed(val failure: BootstrapFailure) : SetupStatus
    data object NeedsRescan : SetupStatus
    data object Rescanning : SetupStatus
    data class Ready(val partialRestore: Boolean = false) : SetupStatus

    val inProgress: Boolean get() = this == Waiting || this == Rescanning
}

class WalletSetup(private val bootstrap: () -> WalletBootstrap?) {
    @Volatile
    var status: SetupStatus = SetupStatus.None
        private set

    private var request: SetupRequest? = null
    private var rescanPassphrase: String? = null

    val hasRescanPassphrase: Boolean get() = rescanPassphrase != null

    fun begin(request: SetupRequest) {
        this.request = request
        rescanPassphrase = null
        status = SetupStatus.Waiting
    }

    fun retry() {
        if (request != null) status = SetupStatus.Waiting
    }

    fun cancel() {
        request = null
        rescanPassphrase = null
        status = SetupStatus.None
    }

    fun resumeRescan(passphrase: String) {
        rescanPassphrase = passphrase
        status = SetupStatus.NeedsRescan
    }

    fun markRescanPending() {
        status = SetupStatus.NeedsRescan
    }

    fun step(nodeSynced: Boolean): SetupStatus {
        when (status) {
            SetupStatus.Waiting -> createWallet()
            SetupStatus.NeedsRescan -> if (nodeSynced) rescan()
            else -> Unit
        }
        return status
    }

    private fun createWallet() {
        val req = request ?: return
        val node = bootstrap() ?: return
        when (val result = if (req.restore) node.recover(req.phrase, req.passphrase) else node.create(req.phrase, req.passphrase)) {
            is BootstrapResult.Created -> {
                request = null
                if (req.restore) {
                    rescanPassphrase = req.passphrase
                    status = SetupStatus.NeedsRescan
                } else {
                    status = SetupStatus.Ready()
                }
            }
            is BootstrapResult.Failed -> status = if (result.reason == BootstrapFailure.NodeNotReady) SetupStatus.Waiting else SetupStatus.Failed(result.reason)
        }
    }

    private fun rescan() {
        val pass = rescanPassphrase ?: return
        val node = bootstrap() ?: return
        status = SetupStatus.Rescanning
        status = when (val outcome = node.rescan(pass)) {
            RescanOutcome.Full -> done()
            RescanOutcome.Partial -> done(partial = true)
            is RescanOutcome.Failed -> if (outcome.reason == BootstrapFailure.WrongPassphrase) {
                rescanPassphrase = null
                SetupStatus.NeedsRescan
            } else {
                SetupStatus.NeedsRescan
            }
        }
    }

    private fun done(partial: Boolean = false): SetupStatus {
        rescanPassphrase = null
        return SetupStatus.Ready(partial)
    }
}
