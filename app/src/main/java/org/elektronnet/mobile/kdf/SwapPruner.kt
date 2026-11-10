package org.elektronnet.mobile.kdf

import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import org.elektronnet.mobile.node.Json

data class CompactSwapReceipt(
    val uuid: String,
    val state: String = "Finished",
    val myCoin: String,
    val otherCoin: String,
    val myAmount: BigDecimal,
    val otherAmount: BigDecimal,
    val executionPrice: BigDecimal,
    val timestamp: Long,
    val secretHash: String = "",
    val isPruned: Boolean = true,
) {
    fun toSwapStatus(): SwapStatus {
        return SwapStatus(
            uuid = uuid,
            state = state,
            error = null,
            events = listOf(SwapEvent("Finished", timestamp)),
            myCoin = myCoin,
            otherCoin = otherCoin,
            myAmount = myAmount,
            otherAmount = otherAmount,
            isPruned = true,
            secretHash = secretHash.ifEmpty { null },
        )
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "uuid" to uuid,
            "state" to state,
            "my_coin" to myCoin,
            "other_coin" to otherCoin,
            "my_amount" to myAmount.toPlainString(),
            "other_amount" to otherAmount.toPlainString(),
            "execution_price" to executionPrice.toPlainString(),
            "timestamp" to timestamp,
            "secret_hash" to secretHash,
            "is_pruned" to isPruned,
        )
    }

    companion object {
        fun fromMap(m: Map<*, *>): CompactSwapReceipt? {
            val uuid = m["uuid"]?.toString() ?: return null
            val state = m["state"]?.toString() ?: "Finished"
            val myCoin = m["my_coin"]?.toString().orEmpty()
            val otherCoin = m["other_coin"]?.toString().orEmpty()
            val myAmount = try { BigDecimal(m["my_amount"]?.toString() ?: "0") } catch (_: Exception) { BigDecimal.ZERO }
            val otherAmount = try { BigDecimal(m["other_amount"]?.toString() ?: "0") } catch (_: Exception) { BigDecimal.ZERO }
            val executionPrice = try { BigDecimal(m["execution_price"]?.toString() ?: "0") } catch (_: Exception) { BigDecimal.ZERO }
            val timestamp = (m["timestamp"] as? Number)?.toLong() ?: 0L
            val secretHash = m["secret_hash"]?.toString().orEmpty()
            return CompactSwapReceipt(
                uuid = uuid,
                state = state,
                myCoin = myCoin,
                otherCoin = otherCoin,
                myAmount = myAmount,
                otherAmount = otherAmount,
                executionPrice = executionPrice,
                timestamp = timestamp,
                secretHash = secretHash,
                isPruned = true,
            )
        }
    }
}

class SwapPruner(private val kdfDir: File) {
    private val receiptsFile = File(kdfDir, "pruned_swaps.json")
    private val dbDir = File(kdfDir, "db")

    @Synchronized
    fun getPrunedReceipts(): List<CompactSwapReceipt> {
        if (!receiptsFile.exists() || !receiptsFile.isFile) return emptyList()
        return try {
            val parsed = Json.parse(receiptsFile.readText()) as? List<*> ?: return emptyList()
            parsed.mapNotNull { (it as? Map<*, *>)?.let { map -> CompactSwapReceipt.fromMap(map) } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveReceipts(receipts: List<CompactSwapReceipt>) {
        val list = receipts.map { it.toMap() }
        val json = Json.stringify(list)
        receiptsFile.writeText(json)
    }

    @Synchronized
    fun pruneSwap(status: SwapStatus): CompactSwapReceipt {
        val existing = getPrunedReceipts().toMutableList()
        val already = existing.find { it.uuid == status.uuid }
        if (already != null) {
            deleteRawFiles(status.uuid)
            return already
        }

        val price = if (status.myAmount > BigDecimal.ZERO) {
            try {
                status.otherAmount.divide(status.myAmount, 8, RoundingMode.HALF_UP)
            } catch (_: Exception) {
                BigDecimal.ZERO
            }
        } else BigDecimal.ZERO

        val ts = status.events.lastOrNull()?.timestamp ?: System.currentTimeMillis()
        val receipt = CompactSwapReceipt(
            uuid = status.uuid,
            state = status.state,
            myCoin = status.myCoin,
            otherCoin = status.otherCoin,
            myAmount = status.myAmount,
            otherAmount = status.otherAmount,
            executionPrice = price,
            timestamp = ts,
            secretHash = status.secretHash.orEmpty(),
            isPruned = true,
        )

        existing.add(receipt)
        saveReceipts(existing)
        deleteRawFiles(status.uuid)
        return receipt
    }

    @Synchronized
    fun pruneAllFinished(swaps: List<SwapStatus>): Int {
        var count = 0
        swaps.filter { it.state.equals("Finished", ignoreCase = true) && !it.isPruned }.forEach {
            pruneSwap(it)
            count++
        }
        return count
    }

    private fun deleteRawFiles(uuid: String) {
        if (!dbDir.exists() || !dbDir.isDirectory) return
        try {
            dbDir.walkTopDown().forEach { file ->
                if (file.isFile && file.name.startsWith(uuid)) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }
}
