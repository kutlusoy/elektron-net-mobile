package org.elektronnet.mobile.kdf

import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import org.elektronnet.mobile.node.Json
import org.elektronnet.mobile.node.JsonException

data class OrderEntry(
    val price: BigDecimal,
    val maxVolume: BigDecimal,
    val minVolume: BigDecimal,
    val address: String = "",
    val uuid: String = "",
)

data class Orderbook(
    val base: String,
    val rel: String,
    val asks: List<OrderEntry>,
    val bids: List<OrderEntry>,
    val timestamp: Long = System.currentTimeMillis(),
)

data class MakerOrder(
    val uuid: String,
    val base: String,
    val rel: String,
    val price: BigDecimal,
    val maxBaseVol: BigDecimal,
    val availableAmount: BigDecimal,
    val cancellable: Boolean,
    val createdAt: Long,
)

data class MyOrders(
    val makerOrders: List<MakerOrder>,
    val takerOrders: List<String>,
)

data class CoinBalance(
    val coin: String,
    val balance: BigDecimal,
    val unspendableBalance: BigDecimal,
    val address: String,
)

data class SwapEvent(
    val type: String,
    val timestamp: Long,
)

data class SwapStatus(
    val uuid: String,
    val state: String,
    val error: String?,
    val events: List<SwapEvent>,
    val myCoin: String,
    val otherCoin: String,
    val myAmount: BigDecimal,
    val otherAmount: BigDecimal,
    val isPruned: Boolean = false,
    val secretHash: String? = null,
)

data class WithdrawResult(
    val txHex: String,
    val txHash: String,
    val from: List<String>,
    val to: List<String>,
    val totalAmount: BigDecimal,
    val feeAmount: BigDecimal,
    val coin: String,
)

class KdfClient(
    val rpcHost: String = KdfConfig.DEFAULT_RPC_HOST,
    val rpcPort: Int = KdfConfig.DEFAULT_RPC_PORT,
    var rpcPasswordProvider: () -> String = { "" },
    private val connectTimeoutMs: Int = 3_000,
) {
    private val endpoint: String get() = "http://$rpcHost:$rpcPort"

    fun call(method: String, extra: Map<String, Any?> = emptyMap(), timeoutMs: Int = 15_000): Map<*, *> {
        val pw = rpcPasswordProvider()
        val payload = linkedMapOf<String, Any?>("userpass" to pw, "method" to method)
        payload.putAll(extra)
        val body = Json.stringify(payload)

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (status !in 200..299) {
                throw IOException("KDF RPC error HTTP $status: $text")
            }
            val reply = try {
                Json.parse(text) as? Map<*, *>
            } catch (e: JsonException) {
                null
            } ?: throw IOException("KDF RPC returned non-JSON reply: $text")

            val error = reply["error"]
            if (error != null) {
                throw IOException("KDF RPC method $method failed: $error")
            }
            return reply
        } finally {
            connection.disconnect()
        }
    }

    fun callV2(method: String, params: Map<String, Any?> = emptyMap(), timeoutMs: Int = 15_000): Map<*, *> {
        val pw = rpcPasswordProvider()
        val payload = linkedMapOf<String, Any?>(
            "userpass" to pw,
            "method" to method,
            "mmrpc" to "2.0",
            "params" to params,
        )
        val body = Json.stringify(payload)

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (status !in 200..299) {
                throw IOException("KDF RPC v2 error HTTP $status: $text")
            }
            val reply = Json.parse(text) as? Map<*, *> ?: throw IOException("KDF RPC returned non-JSON: $text")
            val error = reply["error"]
            if (error != null) {
                throw IOException("KDF RPC v2 method $method failed: $error")
            }
            return reply
        } finally {
            connection.disconnect()
        }
    }

    fun version(): String {
        val res = call("version", timeoutMs = 2_000)
        return res["result"]?.toString() ?: res.toString()
    }

    fun orderbook(base: String, rel: String): Orderbook {
        val res = call("orderbook", mapOf("base" to base, "rel" to rel))
        val asks = (res["asks"] as? List<*>)?.mapNotNull { parseOrderEntry(it) }.orEmpty()
        val bids = (res["bids"] as? List<*>)?.mapNotNull { parseOrderEntry(it) }.orEmpty()
        return Orderbook(base = base, rel = rel, asks = asks, bids = bids)
    }

    fun myBalance(coin: String): CoinBalance {
        val res = call("my_balance", mapOf("coin" to coin))
        val bal = toDecimal(res["balance"])
        val unspendable = toDecimal(res["unspendable_balance"])
        val address = res["address"]?.toString().orEmpty()
        return CoinBalance(coin = coin, balance = bal, unspendableBalance = unspendable, address = address)
    }

    fun setprice(base: String, rel: String, price: String, volume: String, cancelPrevious: Boolean = false): Map<*, *> {
        return call("setprice", mapOf(
            "base" to base,
            "rel" to rel,
            "price" to price,
            "volume" to volume,
            "cancel_previous" to cancelPrevious,
        ))
    }

    fun buy(base: String, rel: String, price: String, volume: String): String? {
        val res = call("buy", mapOf(
            "base" to base,
            "rel" to rel,
            "price" to price,
            "volume" to volume,
        ))
        return findUuid(res)
    }

    fun cancelOrder(uuid: String): Boolean {
        val res = call("cancel_order", mapOf("uuid" to uuid))
        return res["result"] == "success" || res["error"] == null
    }

    fun myOrders(): MyOrders {
        val res = call("my_orders")
        val result = res["result"] as? Map<*, *> ?: return MyOrders(emptyList(), emptyList())
        val makerOrders = mutableListOf<MakerOrder>()
        val makerMap = result["maker_orders"] as? Map<*, *>
        makerMap?.forEach { (key, value) ->
            val m = value as? Map<*, *> ?: return@forEach
            val uuid = m["uuid"]?.toString() ?: key.toString()
            makerOrders.add(
                MakerOrder(
                    uuid = uuid,
                    base = m["base"]?.toString().orEmpty(),
                    rel = m["rel"]?.toString().orEmpty(),
                    price = toDecimal(m["price"]),
                    maxBaseVol = toDecimal(m["max_base_vol"]),
                    availableAmount = toDecimal(m["available_amount"]),
                    cancellable = m["cancellable"] as? Boolean ?: true,
                    createdAt = (m["created_at"] as? Number)?.toLong() ?: 0L,
                )
            )
        }
        makerOrders.sortByDescending { it.createdAt }

        val takerOrders = mutableListOf<String>()
        val takerMap = result["taker_orders"] as? Map<*, *>
        takerMap?.keys?.forEach { takerOrders.add(it.toString()) }

        return MyOrders(makerOrders = makerOrders, takerOrders = takerOrders)
    }

    fun mySwapStatus(uuid: String): SwapStatus {
        val res = callV2("my_swap_status", mapOf("uuid" to uuid))
        val result = res["result"] as? Map<*, *> ?: return SwapStatus(uuid, "Unknown", null, emptyList(), "", "", BigDecimal.ZERO, BigDecimal.ZERO)
        val swapData = result["swap_data"] as? Map<*, *> ?: result
        val errorEvents = result["error_events"] as? List<*>
        val eventsList = (swapData["events"] as? List<*>).orEmpty()
        val parsedEvents = eventsList.mapNotNull { e ->
            val em = e as? Map<*, *> ?: return@mapNotNull null
            val ev = em["event"] as? Map<*, *> ?: return@mapNotNull null
            val type = ev["type"]?.toString().orEmpty()
            val ts = (ev["timestamp"] as? Number)?.toLong() ?: 0L
            SwapEvent(type, ts)
        }

        var state = "Running"
        val types = parsedEvents.map { it.type }
        if ("Finished" in types) state = "Finished"
        else if ("Failed" in types) state = "Failed"
        else if ("Refunded" in types) state = "Refunded"

        val myInfo = swapData["my_info"] as? Map<*, *>
        val otherInfo = swapData["other_info"] as? Map<*, *>

        val myCoin = myInfo?.get("my_coin")?.toString() ?: myInfo?.get("coin")?.toString() ?: swapData["maker_coin"]?.toString().orEmpty()
        val otherCoin = myInfo?.get("other_coin")?.toString() ?: otherInfo?.get("coin")?.toString() ?: swapData["taker_coin"]?.toString().orEmpty()
        val myAmount = toDecimal(myInfo?.get("my_amount") ?: myInfo?.get("amount") ?: swapData["maker_amount"])
        val otherAmount = toDecimal(myInfo?.get("other_amount") ?: otherInfo?.get("amount") ?: swapData["taker_amount"])

        var secretHash: String? = null
        for (e in eventsList) {
            val em = e as? Map<*, *> ?: continue
            val ev = em["event"] as? Map<*, *> ?: continue
            val data = ev["data"] as? Map<*, *> ?: continue
            val sh = data["secret_hash"]?.toString()
            if (!sh.isNullOrEmpty()) {
                secretHash = sh
                break
            }
        }

        val error = errorEvents?.firstOrNull()?.toString()
        return SwapStatus(
            uuid = uuid,
            state = state,
            error = error,
            events = parsedEvents,
            myCoin = myCoin,
            otherCoin = otherCoin,
            myAmount = myAmount,
            otherAmount = otherAmount,
            isPruned = false,
            secretHash = secretHash,
        )
    }

    fun myRecentSwaps(limit: Int = 10): List<SwapStatus> {
        val res = callV2("my_recent_swaps", mapOf("limit" to limit))
        val result = res["result"] as? Map<*, *> ?: return emptyList()
        val swaps = result["swaps"] as? List<*> ?: return emptyList()
        return swaps.mapNotNull { s ->
            val sm = s as? Map<*, *> ?: return@mapNotNull null
            val swapData = sm["swap_data"] as? Map<*, *> ?: sm
            val uuid = swapData["uuid"]?.toString() ?: sm["uuid"]?.toString().orEmpty()
            if (uuid.isEmpty()) return@mapNotNull null
            try {
                mySwapStatus(uuid)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun enableElectrum(coin: String, servers: List<String>): Map<*, *> {
        val serverList = servers.map { mapOf("url" to it, "protocol" to "TCP") }
        return call("electrum", mapOf("coin" to coin, "servers" to serverList))
    }

    fun enableEth(
        coin: String,
        urls: List<String>,
        swapContract: String,
    ): Map<*, *> {
        return call("enable", mapOf(
            "coin" to coin,
            "urls" to urls,
            "swap_contract_address" to swapContract,
            "mm2" to 1,
        ))
    }

    fun withdraw(
        coin: String,
        toAddress: String,
        amount: String? = null,
        max: Boolean = false,
    ): WithdrawResult {
        val payload = mutableMapOf<String, Any>(
            "coin" to coin,
            "to" to toAddress,
        )
        if (max) {
            payload["max"] = true
        } else if (amount != null && amount.isNotBlank()) {
            payload["amount"] = amount
        } else {
            payload["max"] = true
        }
        val res = call("withdraw", payload)
        val txHex = res["tx_hex"]?.toString().orEmpty()
        val txHash = res["tx_hash"]?.toString().orEmpty()
        val from = (res["from"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val to = (res["to"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val total = toDecimal(res["total_amount"])
        val feeObj = res["fee_details"] as? Map<*, *>
        val fee = toDecimal(feeObj?.get("amount"))
        return WithdrawResult(
            txHex = txHex,
            txHash = txHash,
            from = from,
            to = to,
            totalAmount = total,
            feeAmount = fee,
            coin = coin,
        )
    }

    fun sendRawTransaction(coin: String, txHex: String): String {
        val res = call("send_raw_transaction", mapOf("coin" to coin, "tx_hex" to txHex))
        val txHash = res["tx_hash"]?.toString()
        if (txHash.isNullOrBlank()) {
            val err = res["error"]?.toString() ?: "Send raw transaction failed"
            throw RuntimeException(err)
        }
        return txHash
    }

    private fun parseOrderEntry(obj: Any?): OrderEntry? {
        val m = obj as? Map<*, *> ?: return null
        val price = toDecimal(m["price"])
        val maxVol = toDecimal(m["maxvolume"] ?: m["volume"])
        val minVol = toDecimal(m["min_volume"] ?: m["minvolume"] ?: BigDecimal.ZERO)
        val address = m["address"]?.toString().orEmpty()
        val uuid = m["uuid"]?.toString().orEmpty()
        return OrderEntry(price = price, maxVolume = maxVol, minVolume = minVol, address = address, uuid = uuid)
    }

    private fun toDecimal(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        is Number -> BigDecimal(v.toString())
        is String -> runCatching { BigDecimal(v) }.getOrDefault(BigDecimal.ZERO)
        else -> BigDecimal.ZERO
    }

    private fun findUuid(obj: Any?): String? {
        when (obj) {
            is Map<*, *> -> {
                for ((k, v) in obj) {
                    if (k?.toString() == "uuid" && v is String && v.length == 36 && v.count { it == '-' } == 4) {
                        return v
                    }
                    val found = findUuid(v)
                    if (found != null) return found
                }
            }
            is List<*> -> {
                for (item in obj) {
                    val found = findUuid(item)
                    if (found != null) return found
                }
            }
        }
        return null
    }
}
