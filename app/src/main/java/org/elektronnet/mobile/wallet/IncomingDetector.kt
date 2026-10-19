package org.elektronnet.mobile.wallet

interface SeenStore {
    var ids: List<String>?
}

class IncomingDetector(private val store: SeenStore, private val keep: Int = 400) {
    fun poll(recent: List<TxItem>): List<TxItem> {
        val seen = store.ids
        val current = recent.filter { it.kind == TxKind.Received && it.status != TxStatus.Conflicted && it.status != TxStatus.Abandoned }
        val fresh = if (seen == null) emptyList() else current.filter { it.txid !in seen }
        val merged = ((seen ?: emptyList()) + current.map { it.txid }).distinct().takeLast(keep)
        if (merged != seen) store.ids = merged
        return fresh
    }
}
