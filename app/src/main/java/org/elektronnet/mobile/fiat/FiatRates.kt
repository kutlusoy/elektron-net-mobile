package org.elektronnet.mobile.fiat

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import org.elektronnet.mobile.node.Json

data class FiatRate(val perElek: BigDecimal, val quote: String, val fetchedAtMs: Long, val source: String = "")

sealed interface RateResult {
    data class Found(val rate: FiatRate) : RateResult
    data object NoPair : RateResult
    data object Failed : RateResult
    data object Limited : RateResult
}

object PriceParser {
    fun extract(json: String, path: String): BigDecimal? = runCatching {
        var node: Any? = Json.parse(json)
        for (segment in path.split('.')) {
            node = when (node) {
                is Map<*, *> -> if (segment == "*") node.values.firstOrNull() else node[segment]
                is List<*> -> segment.toIntOrNull()?.let { node.getOrNull(it) }
                else -> null
            }
        }
        when (node) {
            is Number -> BigDecimal(node.toString())
            is String -> BigDecimal(node.trim())
            else -> null
        }
    }.getOrNull()

    fun rate(provider: PriceProvider, json: String, quote: String, order: PairOrder, now: Long, ticker: String = ELEK_TICKER): FiatRate? {
        val price = extract(json, provider.requestPath(quote, order, ticker))?.takeIf { it.signum() > 0 } ?: return null
        val perElek = if (order == PairOrder.ElekFirst) price else BigDecimal.ONE.divide(price, MathContext(12, RoundingMode.HALF_EVEN))
        return FiatRate(perElek, quote, now, provider.name)
    }
}

object FiatRates {
    const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
    const val MIN_GAP_MS = 60_000L
    const val BACKOFF_MS = 15 * 60_000L
    const val POLL_MS = 5 * 60_000L

    // fetch returns the response body, or null when the service has no such market (HTTP 4xx); it throws on network errors.
    // The chosen service is asked first, the fallbacks one after the other only while no rate was found. skip leaves out
    // services that are resting after a rate limit answer, onLimited reports one that just answered with it.
    fun refresh(
        settings: FiatSettings,
        now: Long,
        skip: (PriceProvider) -> Boolean = { false },
        onLimited: (PriceProvider) -> Unit = {},
        fetch: (String) -> String?,
    ): RateResult {
        val chain = (listOf(settings.provider) + settings.fallbacks).distinctBy { it.id }
        var worst: RateResult = RateResult.NoPair
        for (provider in chain) {
            if (skip(provider)) {
                worst = RateResult.Limited
                continue
            }
            when (val result = attempt(provider, settings, now, fetch)) {
                is RateResult.Found -> return result
                RateResult.Limited -> { onLimited(provider); if (worst != RateResult.Failed) worst = result }
                RateResult.Failed -> worst = result
                RateResult.NoPair -> Unit
            }
        }
        return worst
    }

    private fun attempt(provider: PriceProvider, settings: FiatSettings, now: Long, fetch: (String) -> String?): RateResult {
        val body = try {
            fetch(provider.requestUrl(settings.quote, settings.order, settings.ticker)) ?: return RateResult.NoPair
        } catch (e: RateLimitException) {
            return RateResult.Limited
        } catch (e: Exception) {
            return RateResult.Failed
        }
        val rate = PriceParser.rate(provider, body, settings.quote, settings.order, now, settings.ticker)
        return if (rate != null) RateResult.Found(rate) else RateResult.NoPair
    }

    fun usable(rate: FiatRate?, settings: FiatSettings, now: Long): FiatRate? =
        rate?.takeIf { settings.enabled && it.quote == settings.quote && now - it.fetchedAtMs in 0..MAX_AGE_MS }

    fun value(lep: Long, rate: FiatRate): BigDecimal = BigDecimal(lep).multiply(rate.perElek).movePointLeft(8)

    private fun digits(code: String): Int = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrNull()?.takeIf { it >= 0 } ?: 2

    // parses an amount typed in the fiat currency into lepton, null when it is not a plain positive number
    fun lepFromFiat(text: String, rate: FiatRate): Long? {
        val t = text.trim().replace(',', '.')
        if (!Regex("[0-9]+(\\.[0-9]{1,8})?|\\.[0-9]{1,8}").matches(t)) return null
        val fiat = BigDecimal(if (t.startsWith(".")) "0$t" else t)
        val lep = fiat.divide(rate.perElek, MathContext(24, RoundingMode.HALF_UP)).movePointRight(8).setScale(0, RoundingMode.HALF_UP)
        return if (lep.signum() > 0 && lep <= BigDecimal(org.elektronnet.mobile.wallet.Amount.MAX_LEP)) lep.longValueExact() else null
    }

    // plain text for an input field, without grouping
    fun inputText(lep: Long, rate: FiatRate): String =
        value(lep, rate).setScale(digits(rate.quote), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun format(lep: Long, rate: FiatRate, locale: Locale = Locale.getDefault()): String {
        val value = value(lep, rate)
        val digits = digits(rate.quote)
        val tiny = value.signum() != 0 && value.abs() < BigDecimal.ONE.movePointLeft(digits)
        val format = NumberFormat.getInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = if (tiny) maxOf(digits, 6) else digits
            roundingMode = RoundingMode.HALF_UP
        }
        return format.format(value) + " " + rate.quote
    }
}
