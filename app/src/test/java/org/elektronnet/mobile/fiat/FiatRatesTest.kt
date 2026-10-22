package org.elektronnet.mobile.fiat

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiatRatesTest {
    private fun provider(id: String) = PriceProviders.builtin.first { it.id == id }

    @Test
    fun extractsValuesAlongThePath() {
        assertEquals(BigDecimal("0.05"), PriceParser.extract("""{"price":"0.05"}""", "price"))
        assertEquals(BigDecimal("1.5"), PriceParser.extract("""{"data":[{"lastPr":1.5}]}""", "data.0.lastPr"))
        assertEquals(BigDecimal("2.25"), PriceParser.extract("""{"result":{"ELEKEUR":{"c":["2.25","1"]}}}""", "result.*.c.0"))
        assertNull(PriceParser.extract("""{"price":"abc"}""", "price"))
        assertNull(PriceParser.extract("""{"price":"1"}""", "other"))
        assertNull(PriceParser.extract("not json", "price"))
    }

    @Test
    fun buildsRequestsForBothPairOrders() {
        val binance = provider("binance")
        assertEquals("https://api.binance.com/api/v3/ticker/price?symbol=ELEKEUR", binance.requestUrl("EUR", PairOrder.ElekFirst))
        assertEquals("https://api.binance.com/api/v3/ticker/price?symbol=EURELEK", binance.requestUrl("EUR", PairOrder.FiatFirst))
        assertEquals("https://tradeogre.com/api/v1/ticker/ELEK-USDT", provider("tradeogre").requestUrl("USDT", PairOrder.ElekFirst))
    }

    @Test
    fun aFixedAddressCanStandInForAMarketWithQuotePlaceholderInThePath() {
        val gecko = PriceProvider("own", "CoinGecko test", "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies={quote}", "bitcoin.{quote}")
        assertTrue(gecko.valid())
        assertEquals("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=eur", gecko.requestUrl("EUR", PairOrder.ElekFirst))
        val settings = FiatSettings(enabled = true, providerId = "own", quote = "EUR", customProviders = listOf(gecko))
        val found = FiatRates.refresh(settings, 5L) { """{"bitcoin":{"eur":61234.5,"usd":66000}}""" } as RateResult.Found
        assertEquals(0, BigDecimal("61234.5").compareTo(found.rate.perElek))
        val usd = FiatRates.refresh(settings.copy(quote = "USD"), 5L) { """{"bitcoin":{"eur":61234.5,"usd":66000}}""" } as RateResult.Found
        assertEquals(0, BigDecimal("66000").compareTo(usd.rate.perElek))
    }

    @Test
    fun invertsTheRateForFiatFirstPairs() {
        val settings = FiatSettings(enabled = true, quote = "EUR", order = PairOrder.FiatFirst)
        val result = FiatRates.refresh(settings, 1000L) { """{"price":"20"}""" } as RateResult.Found
        assertEquals(0, BigDecimal("0.05").compareTo(result.rate.perElek))
        val direct = FiatRates.refresh(settings.copy(order = PairOrder.ElekFirst), 1000L) { """{"price":"20"}""" } as RateResult.Found
        assertEquals(0, BigDecimal("20").compareTo(direct.rate.perElek))
    }

    @Test
    fun missingMarketsAndErrorsGiveNoRate() {
        val settings = FiatSettings(enabled = true)
        assertEquals(RateResult.NoPair, FiatRates.refresh(settings, 0L) { null })
        assertEquals(RateResult.NoPair, FiatRates.refresh(settings, 0L) { """{"msg":"unknown symbol"}""" })
        assertEquals(RateResult.NoPair, FiatRates.refresh(settings, 0L) { """{"price":"0"}""" })
        assertEquals(RateResult.Failed, FiatRates.refresh(settings, 0L) { throw java.io.IOException("down") })
    }

    @Test
    fun anOldOrForeignRateIsNotUsed() {
        val settings = FiatSettings(enabled = true, quote = "EUR")
        val rate = FiatRate(BigDecimal("2"), "EUR", 1000L)
        assertEquals(rate, FiatRates.usable(rate, settings, 2000L))
        assertNull(FiatRates.usable(rate, settings, 1000L + FiatRates.MAX_AGE_MS + 1))
        assertNull(FiatRates.usable(rate, settings.copy(quote = "USD"), 2000L))
        assertNull(FiatRates.usable(rate, settings.copy(enabled = false), 2000L))
        assertNull(FiatRates.usable(rate, settings, 500L))
    }

    @Test
    fun formatsAmounts() {
        val rate = FiatRate(BigDecimal("1.5"), "EUR", 0L)
        assertEquals("3.00 EUR", FiatRates.format(200_000_000L, rate, Locale.US))
        assertEquals("1,500.00 EUR", FiatRates.format(100_000_000_000L, rate, Locale.US))
        assertEquals("3,00 EUR", FiatRates.format(200_000_000L, rate, Locale.GERMANY))
        assertEquals("150 JPY", FiatRates.format(100_000_000L, FiatRate(BigDecimal("150"), "JPY", 0L), Locale.US))
        assertEquals("0.000015 EUR", FiatRates.format(1_000L, rate, Locale.US))
        assertEquals("0.00 EUR", FiatRates.format(0L, rate, Locale.US))
        assertEquals("1.50 FOO", FiatRates.format(100_000_000L, FiatRate(BigDecimal("1.5"), "FOO", 0L), Locale.US))
    }

    @Test
    fun settingsSurviveSavingAndLoadingAndRejectBadInput() {
        val custom = PriceProvider("own", "My exchange", "https://example.org/t/{BASE}/{QUOTE}", "last")
        val settings = FiatSettings(true, "own", "pln", PairOrder.FiatFirst, true, listOf(custom, custom.copy(id = "bad", url = "http://x/{BASE}{QUOTE}")), listOf("pln", "USD", "x"))
        val loaded = FiatSettings.decode(settings.normalized().encode())
        assertEquals("own", loaded.providerId)
        assertEquals("PLN", loaded.quote)
        assertEquals(PairOrder.FiatFirst, loaded.order)
        assertTrue(loaded.mainFiat && loaded.enabled)
        assertEquals(listOf(custom), loaded.customProviders)
        assertEquals(listOf("PLN"), loaded.customQuotes)
        assertEquals("PLN_ELEK", loaded.pairLabel)
        assertFalse(FiatSettings.decode("garbage").enabled)
        assertFalse(FiatSettings.decode(null).enabled)
        assertEquals("binance", FiatSettings(providerId = "gone").normalized().providerId)
        assertNull(FiatCodes.normalize("ELEK"))
        assertNull(FiatCodes.normalize("e"))
        assertEquals("EUR", FiatCodes.normalize(" eur "))
        assertTrue(PriceProvider.validUrl("https://example.org/fixed"))
        assertFalse(PriceProvider.validUrl("https://"))
        assertFalse(PriceProvider.validUrl("ftp://example.org/{BASE}/{QUOTE}"))
    }
}

class FiatInputTest {
    private val rate = FiatRate(BigDecimal("2.5"), "EUR", 0L)

    @Test
    fun convertsTypedFiatAmountsToLepton() {
        assertEquals(400_000_000L, FiatRates.lepFromFiat("10", rate))
        assertEquals(40_000_000L, FiatRates.lepFromFiat("1,00", rate))
        assertEquals(20_000_000L, FiatRates.lepFromFiat(".5", rate))
        assertNull(FiatRates.lepFromFiat("0", rate))
        assertNull(FiatRates.lepFromFiat("abc", rate))
        assertNull(FiatRates.lepFromFiat("-1", rate))
        assertNull(FiatRates.lepFromFiat("1e3", rate))
    }

    @Test
    fun givesInputTextWithoutGroupingAndTrailingZeros() {
        assertEquals("10", FiatRates.inputText(400_000_000L, rate))
        assertEquals("1.25", FiatRates.inputText(50_000_000L, rate))
        assertEquals("0", FiatRates.inputText(1L, rate))
        assertEquals("15000", FiatRates.inputText(600_000_000_000L, rate))
    }

    @Test
    fun fiatEntryAppliesToTheSendAmountCheck() {
        assertEquals(org.elektronnet.mobile.ui.model.AmountState.Ok(400_000_000L), org.elektronnet.mobile.ui.model.SendInput.amount("10", 500_000_000L, rate))
        assertEquals(org.elektronnet.mobile.ui.model.AmountState.Over(100_000_000L), org.elektronnet.mobile.ui.model.SendInput.amount("10", 100_000_000L, rate))
        assertEquals(org.elektronnet.mobile.ui.model.AmountState.Invalid, org.elektronnet.mobile.ui.model.SendInput.amount("x", 100_000_000L, rate))
    }
}

class FiatReceiveTest {
    @Test
    fun aFiatRequestBecomesAnElekAmountInTheLink() {
        val rate = FiatRate(BigDecimal("2.5"), "EUR", 0L)
        val uris = org.elektronnet.mobile.wallet.PaymentUri("elek", org.elektronnet.mobile.wallet.AddressCheck(false))
        val model = org.elektronnet.mobile.ui.model.ReceiveUiModel.build("elek1qabc", uris, "10", "", true, rate)
        assertEquals(400_000_000L, model.amountLep)
        assertTrue(model.uri.contains("amount=4"))
        assertFalse(model.amountInvalid)
        assertTrue(org.elektronnet.mobile.ui.model.ReceiveUiModel.build("elek1qabc", uris, "x", "", true, rate).amountInvalid)
    }
}

class FiatStandInTest {
    private val bitcoin = PriceProvider("own", "Test", "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,eur,try", "")
    private val response = """{"bitcoin":{"usd":81762,"eur":72940,"try":4057676}}"""

    @Test
    fun bitcoinOnCoinGeckoStandsInForElekWithoutAPath() {
        assertTrue(bitcoin.valid())
        val settings = FiatSettings(enabled = true, providerId = "own", quote = "TRY", customProviders = listOf(bitcoin))
        val found = FiatRates.refresh(settings, 1L) { response } as RateResult.Found
        assertEquals(0, BigDecimal("4057676").compareTo(found.rate.perElek))
        val eur = FiatRates.refresh(settings.copy(quote = "EUR"), 1L) { response } as RateResult.Found
        assertEquals(0, BigDecimal("72940").compareTo(eur.rate.perElek))
    }

    @Test
    fun otherCoinGeckoAssetsAreRejected() {
        val solana = bitcoin.copy(url = "https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd,eur,try")
        assertTrue(solana.rejectedAsset)
        assertFalse(solana.valid())
        assertFalse(bitcoin.copy(url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,solana&vs_currencies=usd").valid())
        assertFalse(solana.copy(path = "solana.eur").valid())
        assertFalse(bitcoin.copy(url = "https://api.coingecko.com/api/v3/simple/price?vs_currencies=usd").valid())
        assertFalse(FiatSettings.decode(FiatSettings(customProviders = listOf(bitcoin), providerId = "own").encode().replace("ids=bitcoin", "ids=solana")).customProviders.isNotEmpty())
        assertFalse(bitcoin.copy(path = "").copy(url = "https://example.org/x").valid())
    }

    @Test
    fun theElekSymbolOnExchangesCanBeChanged() {
        val binance = PriceProviders.builtin.first { it.id == "binance" }
        assertEquals("https://api.binance.com/api/v3/ticker/price?symbol=ELKEUR", binance.requestUrl("EUR", PairOrder.ElekFirst, "ELK"))
        assertEquals("https://api.binance.com/api/v3/ticker/price?symbol=EURELK", binance.requestUrl("EUR", PairOrder.FiatFirst, "ELK"))
        val settings = FiatSettings(ticker = "elk", quote = "EUR", order = PairOrder.FiatFirst).normalized()
        assertEquals("ELK", settings.ticker)
        assertEquals("EUR_ELK", settings.pairLabel)
        assertEquals("ELK", FiatSettings.decode(settings.encode()).ticker)
        assertEquals("ELEK", FiatSettings(ticker = "x").normalized().ticker)
        var requested = ""
        FiatRates.refresh(settings.copy(enabled = true), 1L) { requested = it; null }
        assertEquals("https://api.binance.com/api/v3/ticker/price?symbol=EURELK", requested)
    }

    @Test
    fun aRateLimitAnswerIsTreatedAsLimitedNotAsMissingMarket() {
        assertEquals(RateResult.Limited, FiatRates.refresh(FiatSettings(enabled = true), 1L) { throw RateLimitException() })
    }
}

class FiatFallbackTest {
    private val settings = FiatSettings(enabled = true, quote = "EUR", fallbackIds = listOf("coinbase", "kucoin"))

    @Test
    fun theNextServiceIsAskedOnlyWhenTheFirstFails() {
        val asked = mutableListOf<String>()
        val result = FiatRates.refresh(settings, 1L) { url ->
            asked.add(url)
            when {
                url.contains("binance") -> throw java.io.IOException("down")
                url.contains("coinbase") -> """{"data":{"amount":"3.5"}}"""
                else -> error("must not be asked")
            }
        } as RateResult.Found
        assertEquals(2, asked.size)
        assertEquals("Coinbase", result.rate.source)
        assertEquals(0, BigDecimal("3.5").compareTo(result.rate.perElek))
    }

    @Test
    fun aFirstServiceThatWorksIsTheOnlyOneAsked() {
        var calls = 0
        val result = FiatRates.refresh(settings, 1L) { calls++; """{"price":"2"}""" } as RateResult.Found
        assertEquals(1, calls)
        assertEquals("Binance", result.rate.source)
    }

    @Test
    fun missingMarketsFallThroughAndTheWorstAnswerIsReported() {
        assertEquals(RateResult.NoPair, FiatRates.refresh(settings, 1L) { null })
        assertEquals(RateResult.Failed, FiatRates.refresh(settings, 1L) { throw java.io.IOException() })
        assertEquals(RateResult.Limited, FiatRates.refresh(settings, 1L) { throw RateLimitException() })
        val limited = mutableListOf<String>()
        FiatRates.refresh(settings, 1L, onLimited = { limited.add(it.id) }) { throw RateLimitException() }
        assertEquals(listOf("binance", "coinbase", "kucoin"), limited)
    }

    @Test
    fun servicesThatRestAreSkipped() {
        val asked = mutableListOf<String>()
        FiatRates.refresh(settings, 1L, skip = { it.id == "binance" }) { url -> asked.add(url); null }
        assertEquals(2, asked.size)
        assertEquals(RateResult.Limited, FiatRates.refresh(settings, 1L, skip = { true }) { null })
    }

    @Test
    fun theListIsCleanedAndSaved() {
        val s = FiatSettings(providerId = "coinbase", fallbackIds = listOf("coinbase", "kucoin", "kucoin", "gone", "mexc")).normalized()
        assertEquals(listOf("kucoin", "mexc"), s.fallbackIds)
        assertEquals(listOf("kucoin", "mexc"), FiatSettings.decode(s.encode()).fallbackIds)
        assertEquals(listOf("KuCoin", "MEXC"), s.fallbacks.map { it.name })
    }
}
