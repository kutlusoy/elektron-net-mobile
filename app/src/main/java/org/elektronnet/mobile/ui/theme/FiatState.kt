package org.elektronnet.mobile.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.fiat.FiatRate
import org.elektronnet.mobile.fiat.FiatRates
import org.elektronnet.mobile.fiat.FiatSettings
import org.elektronnet.mobile.fiat.PriceFetch
import org.elektronnet.mobile.fiat.RateResult
import org.elektronnet.mobile.service.NodeHost

enum class FiatStatus { Idle, Loading, Ok, NoPair, Failed }

object FiatState {
    var settings by mutableStateOf(FiatSettings())
        private set
    var rate: FiatRate? by mutableStateOf(null)
        private set
    var status by mutableStateOf(FiatStatus.Idle)
        private set

    fun load(saved: FiatSettings) {
        settings = saved
    }

    fun apply(next: FiatSettings) {
        val normalized = next.normalized()
        if (normalized.enabled != settings.enabled || normalized.providerId != settings.providerId || normalized.quote != settings.quote || normalized.order != settings.order || normalized.provider != settings.provider) {
            rate = null
            status = FiatStatus.Idle
        }
        settings = normalized
        NodeHost.store?.fiat = normalized
    }

    val current: FiatRate? get() = usable()

    private fun usable(): FiatRate? = FiatRates.usable(rate, settings, System.currentTimeMillis())

    // fiat is shown as the main figure only while a rate is at hand, otherwise the ELEK amount stays in front
    val fiatIsMain: Boolean get() = settings.mainFiat && usable() != null

    fun text(lep: Long): String? {
        val current = usable() ?: return null
        val sign = if (lep < 0) "-" else ""
        return "≈ " + sign + FiatRates.format(Math.abs(lep), current)
    }

    suspend fun refresh() {
        val wanted = settings
        if (!wanted.enabled) return
        if (rate == null) status = FiatStatus.Loading
        val result = withContext(Dispatchers.IO) { FiatRates.refresh(wanted, System.currentTimeMillis(), PriceFetch::get) }
        if (settings != wanted) return
        when (result) {
            is RateResult.Found -> { rate = result.rate; status = FiatStatus.Ok }
            RateResult.NoPair -> { rate = null; status = FiatStatus.NoPair }
            RateResult.Failed -> status = if (usable() != null) FiatStatus.Ok else FiatStatus.Failed
        }
    }
}
