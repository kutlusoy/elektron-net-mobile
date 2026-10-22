package org.elektronnet.mobile.fiat

import org.elektronnet.mobile.node.Json

const val ELEK_TICKER = "ELEK"

enum class PairOrder { ElekFirst, FiatFirst }

data class PriceProvider(val id: String, val name: String, val url: String, val path: String, val builtin: Boolean = false) {
    // a CoinGecko "simple price" address needs no path, the id in the address names the asset
    val geckoIds: List<String>? get() = if (isGeckoSimplePrice(url)) GECKO_IDS.find(url)?.groupValues?.get(1)?.split(',')?.map { it.lowercase() }.orEmpty() else null

    // CoinGecko has no ELEK, so only bitcoin is accepted there as a stand-in for the ELEK price
    val rejectedAsset: Boolean get() = !builtin && geckoIds?.let { ids -> ids.isEmpty() || ids.any { it !in STAND_IN } } == true

    private val effectivePath: String get() = path.ifBlank { geckoIds?.singleOrNull()?.let { "$it.{fiat}" }.orEmpty() }

    // an address that names no {BASE} or {QUOTE} always returns the price of 1 ELEK in the fiat currency, whatever the pair order
    private val directional: Boolean get() = listOf(url, effectivePath).any { text -> DIRECTIONAL.any { it in text } }

    fun orderFor(order: PairOrder): PairOrder = if (directional) order else PairOrder.ElekFirst

    fun valid(): Boolean = id.isNotBlank() && name.isNotBlank() && name.length <= 40 && effectivePath.isNotBlank() && path.length <= 100 && validUrl(url) && !rejectedAsset

    fun requestUrl(quote: String, order: PairOrder, ticker: String = ELEK_TICKER): String = fill(url, quote, orderFor(order), ticker)

    fun requestPath(quote: String, order: PairOrder, ticker: String = ELEK_TICKER): String = fill(effectivePath, quote, orderFor(order), ticker)

    private fun fill(text: String, quote: String, order: PairOrder, ticker: String): String {
        val (base, counter) = if (order == PairOrder.ElekFirst) ticker to quote else quote to ticker
        return text.replace("{BASE}", base).replace("{QUOTE}", counter).replace("{base}", base.lowercase()).replace("{quote}", counter.lowercase())
            .replace("{ELEK}", ticker).replace("{elek}", ticker.lowercase()).replace("{FIAT}", quote).replace("{fiat}", quote.lowercase())
    }

    fun toMap(): Map<String, Any?> = linkedMapOf("id" to id, "name" to name, "url" to url, "path" to path)

    companion object {
        val STAND_IN = setOf("bitcoin")
        private val GECKO_IDS = Regex("[?&]ids=([^&]+)")
        private val DIRECTIONAL = listOf("{BASE}", "{QUOTE}", "{base}", "{quote}")

        fun isGeckoSimplePrice(url: String): Boolean = url.startsWith("https://api.coingecko.com/api/v3/simple/price")

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
        PriceProvider("coingecko", "CoinGecko", "https://api.coingecko.com/api/v3/simple/price?symbols={elek}&vs_currencies={fiat}", "*.{fiat}", true),
    )
}

object FiatCodes {
    val standard = listOf("EUR", "USD", "GBP", "CHF", "TRY", "JPY", "USDT", "BTC")
    private val CODE = Regex("^[A-Z0-9]{2,10}$")

    fun normalize(text: String, ticker: String = ELEK_TICKER): String? = text.trim().uppercase().takeIf { CODE.matches(it) && it != ticker }

    fun normalizeTicker(text: String): String? = text.trim().uppercase().takeIf { CODE.matches(it) }
}

data class FiatSettings(
    val enabled: Boolean = false,
    val providerId: String = PriceProviders.builtin.first().id,
    val quote: String = "EUR",
    val order: PairOrder = PairOrder.ElekFirst,
    val mainFiat: Boolean = false,
    val customProviders: List<PriceProvider> = emptyList(),
    val customQuotes: List<String> = emptyList(),
    val ticker: String = ELEK_TICKER,
    val fallbackIds: List<String> = emptyList(),
) {
    val providers: List<PriceProvider> get() = PriceProviders.builtin + customProviders
    val provider: PriceProvider get() = providers.firstOrNull { it.id == providerId } ?: PriceProviders.builtin.first()
    val fallbacks: List<PriceProvider> get() = fallbackIds.mapNotNull { id -> providers.firstOrNull { it.id == id } }.filter { it.id != provider.id }
    val quotes: List<String> get() = (FiatCodes.standard + customQuotes).distinct()
    val pairLabel: String get() = if (order == PairOrder.ElekFirst) "${ticker}_$quote" else "${quote}_$ticker"

    fun normalized(): FiatSettings {
        val customs = customProviders.filter { it.valid() && !it.builtin && PriceProviders.builtin.none { b -> b.id == it.id } }.distinctBy { it.id }.take(MAX_CUSTOM)
        val symbol = FiatCodes.normalizeTicker(ticker) ?: ELEK_TICKER
        val extra = customQuotes.mapNotNull { FiatCodes.normalize(it, symbol) }.filter { it !in FiatCodes.standard }.distinct().take(MAX_CUSTOM)
        val code = FiatCodes.normalize(quote, symbol) ?: "EUR"
        val known = (PriceProviders.builtin + customs).any { it.id == providerId }
        return copy(
            providerId = if (known) providerId else PriceProviders.builtin.first().id,
            quote = code,
            ticker = symbol,
            fallbackIds = fallbackIds.filter { id -> id != providerId && (PriceProviders.builtin + customs).any { it.id == id } }.distinct().take(MAX_FALLBACKS),
            customProviders = customs,
            customQuotes = (extra + code).filter { it !in FiatCodes.standard }.distinct(),
        )
    }

    fun encode(): String = Json.stringify(
        linkedMapOf(
            "enabled" to enabled, "provider" to providerId, "quote" to quote, "ticker" to ticker, "order" to order.name, "mainFiat" to mainFiat,
            "providers" to customProviders.map { it.toMap() }, "quotes" to customQuotes, "fallbacks" to fallbackIds,
        ),
    )

    companion object {
        const val MAX_CUSTOM = 20
        const val MAX_FALLBACKS = 5

        fun decode(text: String?): FiatSettings {
            val map = runCatching { Json.parse(text ?: return FiatSettings()) as? Map<*, *> }.getOrNull() ?: return FiatSettings()
            val defaults = FiatSettings()
            return FiatSettings(
                enabled = map["enabled"] as? Boolean ?: defaults.enabled,
                providerId = map["provider"] as? String ?: defaults.providerId,
                quote = map["quote"] as? String ?: defaults.quote,
                ticker = map["ticker"] as? String ?: defaults.ticker,
                order = PairOrder.entries.firstOrNull { it.name == map["order"] } ?: defaults.order,
                mainFiat = map["mainFiat"] as? Boolean ?: defaults.mainFiat,
                customProviders = (map["providers"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(PriceProvider::fromMap) },
                customQuotes = (map["quotes"] as? List<*>).orEmpty().mapNotNull { it as? String },
                fallbackIds = (map["fallbacks"] as? List<*>).orEmpty().mapNotNull { it as? String },
            ).normalized()
        }
    }
}
