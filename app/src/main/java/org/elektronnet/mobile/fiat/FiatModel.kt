package org.elektronnet.mobile.fiat

import org.elektronnet.mobile.node.Json

const val ELEK_TICKER = "ELEK"

enum class PairOrder { ElekFirst, FiatFirst }

data class PriceProvider(val id: String, val name: String, val url: String, val path: String, val builtin: Boolean = false) {
    fun valid(): Boolean = id.isNotBlank() && name.isNotBlank() && name.length <= 40 && path.isNotBlank() && path.length <= 100 && validUrl(url)

    fun requestUrl(quote: String, order: PairOrder): String = fill(url, quote, order)

    fun requestPath(quote: String, order: PairOrder): String = fill(path, quote, order)

    private fun fill(text: String, quote: String, order: PairOrder): String {
        val (base, counter) = if (order == PairOrder.ElekFirst) ELEK_TICKER to quote else quote to ELEK_TICKER
        return text.replace("{BASE}", base).replace("{QUOTE}", counter).replace("{base}", base.lowercase()).replace("{quote}", counter.lowercase())
    }

    fun toMap(): Map<String, Any?> = linkedMapOf("id" to id, "name" to name, "url" to url, "path" to path)

    companion object {
        // placeholders are optional, so a fixed address works for tests (the price is then read in whatever currency the address returns)
        fun validUrl(url: String): Boolean = url.length <= 300 && url.startsWith("https://") && url.length > "https://".length && url.none { it.isWhitespace() }

        fun fromMap(map: Map<*, *>): PriceProvider? {
            val provider = PriceProvider(map["id"] as? String ?: return null, map["name"] as? String ?: return null, map["url"] as? String ?: return null, map["path"] as? String ?: return null)
            return provider.takeIf { it.valid() }
        }
    }
}

object PriceProviders {
    val builtin: List<PriceProvider> = listOf(
        PriceProvider("binance", "Binance", "https://api.binance.com/api/v3/ticker/price?symbol={BASE}{QUOTE}", "price", true),
        PriceProvider("coinbase", "Coinbase", "https://api.coinbase.com/v2/prices/{BASE}-{QUOTE}/spot", "data.amount", true),
        PriceProvider("kraken", "Kraken", "https://api.kraken.com/0/public/Ticker?pair={BASE}{QUOTE}", "result.*.c.0", true),
        PriceProvider("kucoin", "KuCoin", "https://api.kucoin.com/api/v1/market/orderbook/level1?symbol={BASE}-{QUOTE}", "data.price", true),
        PriceProvider("mexc", "MEXC", "https://api.mexc.com/api/v3/ticker/price?symbol={BASE}{QUOTE}", "price", true),
        PriceProvider("bitget", "Bitget", "https://api.bitget.com/api/v2/spot/market/tickers?symbol={BASE}{QUOTE}", "data.0.lastPr", true),
        PriceProvider("gate", "Gate.io", "https://api.gateio.ws/api/v4/spot/tickers?currency_pair={BASE}_{QUOTE}", "0.last", true),
        PriceProvider("xeggex", "XeggeX", "https://api.xeggex.com/api/v2/market/getbysymbol/{BASE}%2F{QUOTE}", "lastPrice", true),
        PriceProvider("tradeogre", "TradeOgre", "https://tradeogre.com/api/v1/ticker/{BASE}-{QUOTE}", "price", true),
    )
}

object FiatCodes {
    val standard = listOf("EUR", "USD", "GBP", "CHF", "TRY", "JPY", "USDT", "BTC")
    private val CODE = Regex("^[A-Z0-9]{2,10}$")

    fun normalize(text: String): String? = text.trim().uppercase().takeIf { CODE.matches(it) && it != ELEK_TICKER }
}

data class FiatSettings(
    val enabled: Boolean = false,
    val providerId: String = PriceProviders.builtin.first().id,
    val quote: String = "EUR",
    val order: PairOrder = PairOrder.ElekFirst,
    val mainFiat: Boolean = false,
    val customProviders: List<PriceProvider> = emptyList(),
    val customQuotes: List<String> = emptyList(),
) {
    val providers: List<PriceProvider> get() = PriceProviders.builtin + customProviders
    val provider: PriceProvider get() = providers.firstOrNull { it.id == providerId } ?: PriceProviders.builtin.first()
    val quotes: List<String> get() = (FiatCodes.standard + customQuotes).distinct()
    val pairLabel: String get() = if (order == PairOrder.ElekFirst) "${ELEK_TICKER}_$quote" else "${quote}_$ELEK_TICKER"

    fun normalized(): FiatSettings {
        val customs = customProviders.filter { it.valid() && !it.builtin && PriceProviders.builtin.none { b -> b.id == it.id } }.distinctBy { it.id }.take(MAX_CUSTOM)
        val extra = customQuotes.mapNotNull(FiatCodes::normalize).filter { it !in FiatCodes.standard }.distinct().take(MAX_CUSTOM)
        val code = FiatCodes.normalize(quote) ?: "EUR"
        val known = (PriceProviders.builtin + customs).any { it.id == providerId }
        return copy(
            providerId = if (known) providerId else PriceProviders.builtin.first().id,
            quote = code,
            customProviders = customs,
            customQuotes = (extra + code).filter { it !in FiatCodes.standard }.distinct(),
        )
    }

    fun encode(): String = Json.stringify(
        linkedMapOf(
            "enabled" to enabled, "provider" to providerId, "quote" to quote, "order" to order.name, "mainFiat" to mainFiat,
            "providers" to customProviders.map { it.toMap() }, "quotes" to customQuotes,
        ),
    )

    companion object {
        const val MAX_CUSTOM = 20

        fun decode(text: String?): FiatSettings {
            val map = runCatching { Json.parse(text ?: return FiatSettings()) as? Map<*, *> }.getOrNull() ?: return FiatSettings()
            val defaults = FiatSettings()
            return FiatSettings(
                enabled = map["enabled"] as? Boolean ?: defaults.enabled,
                providerId = map["provider"] as? String ?: defaults.providerId,
                quote = map["quote"] as? String ?: defaults.quote,
                order = PairOrder.entries.firstOrNull { it.name == map["order"] } ?: defaults.order,
                mainFiat = map["mainFiat"] as? Boolean ?: defaults.mainFiat,
                customProviders = (map["providers"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(PriceProvider::fromMap) },
                customQuotes = (map["quotes"] as? List<*>).orEmpty().mapNotNull { it as? String },
            ).normalized()
        }
    }
}
