package org.elektronnet.mobile.wallet

class SetupRequest(
    val phrase: List<String>,
    val passphrase: String,
    val restore: Boolean,
    val walletName: String = WalletBootstrap.DEFAULT_WALLET,
)

sealed interface SetupStatus {
    data object None : SetupStatus
    data object Waiting : SetupStatus
    data class Failed(val failure: BootstrapFailure) : SetupStatus
    data object NeedsRescan : SetupStatus
    data object Rescanning : SetupStatus
    data class Ready(val partialRestore: Boolean = false) : SetupStatus

    val inProgress: Boolean get() = this == Waiting || this == Rescanning
}

// onNameTaken: a wallet with the requested name already exists on the node, returns a free name or null
class WalletSetup(
    private val onNameTaken: (String) -> String? = { null },
    private val bootstrap: (String) -> WalletBootstrap?,
) {
    @Volatile
    var status: SetupStatus = SetupStatus.None
        private set

    private var request: SetupRequest? = null
    var walletName: String = WalletBootstrap.DEFAULT_WALLET
        private set
    private var rescanPassphrase: String? = null

    val hasRescanPassphrase: Boolean get() = rescanPassphrase != null

    fun begin(request: SetupRequest) {
        this.request = request
        walletName = request.walletName
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

    fun markRescanPending(name: String = WalletBootstrap.DEFAULT_WALLET) {
        walletName = name
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
        val node = bootstrap(req.walletName) ?: return
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
            is BootstrapResult.Failed -> {
                val alternative = if (result.reason == BootstrapFailure.WalletExists) onNameTaken(req.walletName)?.takeIf { it != req.walletName } else null
                if (alternative != null) {
                    request = SetupRequest(req.phrase, req.passphrase, req.restore, alternative)
                    walletName = alternative
                    status = SetupStatus.Waiting
                } else {
                    status = if (result.reason == BootstrapFailure.NodeNotReady) SetupStatus.Waiting else SetupStatus.Failed(result.reason)
                }
            }
        }
    }

    private fun rescan() {
        val pass = rescanPassphrase ?: return
        val node = bootstrap(walletName) ?: return
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
