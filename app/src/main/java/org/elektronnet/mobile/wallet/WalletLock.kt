package org.elektronnet.mobile.wallet

object WalletLock {
    // the lock is checked afterwards, a failed walletlock is tried once more
    fun lock(rpc: WalletRpc, wallet: String, passphrase: String? = null): Boolean {
        repeat(ATTEMPTS) {
            runCatching { rpc.call("walletlock", emptyList<Any?>(), wallet) }
            if (locked(rpc, wallet)) return true
        }
        // the unlock window shrinks to one second when the lock cannot be confirmed
        if (passphrase != null) runCatching { rpc.call("walletpassphrase", listOf(passphrase, 1), wallet) }
        return false
    }

    private fun locked(rpc: WalletRpc, wallet: String): Boolean {
        val info = runCatching { rpc.call("getwalletinfo", emptyList<Any?>(), wallet) }.getOrElse { return false }
        val until = (info as? Map<*, *>)?.get("unlocked_until") as? Number ?: return true
        return until.toLong() == 0L
    }

    private const val ATTEMPTS = 2
}
