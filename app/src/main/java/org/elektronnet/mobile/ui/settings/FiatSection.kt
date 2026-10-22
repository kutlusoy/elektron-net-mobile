package org.elektronnet.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.elektronnet.mobile.R
import org.elektronnet.mobile.fiat.FiatCodes
import org.elektronnet.mobile.fiat.FiatRates
import org.elektronnet.mobile.fiat.PairOrder
import org.elektronnet.mobile.fiat.PriceProvider
import org.elektronnet.mobile.ui.theme.FiatState
import org.elektronnet.mobile.ui.theme.FiatStatus
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.wallet.Amount

@Composable
fun FiatSection() {
    val settings = FiatState.settings
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<PriceProvider?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingPair by remember { mutableStateOf(false) }
    ToggleRow(R.string.fiat_switch, R.string.fiat_hint, settings.enabled) { FiatState.apply(settings.copy(enabled = it)) }
    if (!settings.enabled) return

    Text(stringResource(R.string.fiat_provider), style = MaterialTheme.typography.labelLarge)
    Choice(settings.provider.name, settings.providers.map { it.name }) { index -> FiatState.apply(settings.copy(providerId = settings.providers[index].id)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { adding = true }) { Text(stringResource(R.string.fiat_provider_add)) }
        if (!settings.provider.builtin) {
            OutlinedButton(onClick = { editing = settings.provider }) { Text(stringResource(R.string.fiat_provider_edit)) }
        }
    }

    Text(stringResource(R.string.fiat_fallback), style = MaterialTheme.typography.labelLarge)
    Hint(stringResource(R.string.fiat_fallback_hint))
    settings.providers.filter { it.id != settings.providerId }.forEach { other ->
        val position = settings.fallbackIds.indexOf(other.id)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text((if (position >= 0) "${position + 1}. " else "") + other.name, modifier = Modifier.weight(1f))
            Switch(
                checked = position >= 0,
                onCheckedChange = { on -> FiatState.apply(settings.copy(fallbackIds = if (on) settings.fallbackIds + other.id else settings.fallbackIds - other.id)) },
            )
        }
    }

    var tickerText by remember(settings.ticker) { mutableStateOf(settings.ticker) }
    OutlinedTextField(
        value = tickerText,
        onValueChange = { text ->
            tickerText = text.take(10)
            FiatCodes.normalizeTicker(text)?.let { FiatState.apply(FiatState.settings.copy(ticker = it)) }
        },
        label = { Text(stringResource(R.string.fiat_ticker)) },
        isError = FiatCodes.normalizeTicker(tickerText) == null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Hint(stringResource(R.string.fiat_ticker_hint))

    Text(stringResource(R.string.fiat_pair), style = MaterialTheme.typography.labelLarge)
    Choice(settings.quote, settings.quotes) { index -> FiatState.apply(settings.copy(quote = settings.quotes[index])) }
    OutlinedButton(onClick = { addingPair = true }) { Text(stringResource(R.string.fiat_pair_add)) }
    ChoiceRow(
        settings.order == PairOrder.ElekFirst,
        { FiatState.apply(settings.copy(order = PairOrder.ElekFirst)) },
        "${settings.ticker}_${settings.quote}",
        stringResource(R.string.fiat_order_elek, settings.ticker, settings.quote),
    ) {}
    ChoiceRow(
        settings.order == PairOrder.FiatFirst,
        { FiatState.apply(settings.copy(order = PairOrder.FiatFirst)) },
        "${settings.quote}_${settings.ticker}",
        stringResource(R.string.fiat_order_fiat, settings.quote, settings.ticker),
    ) {}

    Text(stringResource(R.string.fiat_main), style = MaterialTheme.typography.labelLarge)
    ChoiceRow(!settings.mainFiat, { FiatState.apply(settings.copy(mainFiat = false)) }, stringResource(R.string.fiat_main_elek), null) {}
    ChoiceRow(settings.mainFiat, { FiatState.apply(settings.copy(mainFiat = true)) }, stringResource(R.string.fiat_main_fiat), null) {}

    val rate = FiatState.rate
    Text(
        when (FiatState.status) {
            FiatStatus.Ok -> stringResource(R.string.fiat_status_ok, rate?.let { FiatRates.format(Amount.LEP_PER_ELEK, it) } ?: "")
            FiatStatus.NoPair -> stringResource(R.string.fiat_status_nopair, settings.pairLabel)
            FiatStatus.Failed -> stringResource(R.string.fiat_status_failed)
            FiatStatus.Limited -> stringResource(R.string.fiat_status_limited)
            FiatStatus.Loading, FiatStatus.Idle -> stringResource(R.string.fiat_status_loading)
        },
        color = MaterialTheme.colorScheme.primary,
    )
    TextButton(onClick = { scope.launch { FiatState.refresh() } }) { Text(stringResource(R.string.fiat_refresh)) }

    if (adding || editing != null) {
        ProviderDialog(
            existing = editing,
            takenIds = settings.providers.map { it.id },
            onDismiss = { adding = false; editing = null },
            onSave = { provider ->
                val others = settings.customProviders.filter { it.id != provider.id }
                FiatState.apply(settings.copy(customProviders = others + provider, providerId = provider.id))
                adding = false; editing = null
            },
            onDelete = { id ->
                FiatState.apply(settings.copy(customProviders = settings.customProviders.filter { it.id != id }, providerId = PriceProviderDefault))
                adding = false; editing = null
            },
        )
    }
    if (addingPair) {
        PairDialog(onDismiss = { addingPair = false }) { code ->
            FiatState.apply(settings.copy(quote = code, customQuotes = settings.customQuotes + code))
            addingPair = false
        }
    }
}

private val PriceProviderDefault get() = org.elektronnet.mobile.fiat.PriceProviders.builtin.first().id

@Composable
private fun Choice(current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(current) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, name -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(index) }) }
        }
    }
}

@Composable
private fun ProviderDialog(existing: PriceProvider?, takenIds: List<String>, onDismiss: () -> Unit, onSave: (PriceProvider) -> Unit, onDelete: (String) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "https://") }
    var path by remember { mutableStateOf(existing?.path ?: "") }
    val id = existing?.id ?: generateSequence(1) { it + 1 }.map { "custom$it" }.first { it !in takenIds }
    val candidate = PriceProvider(id, name.trim(), url.trim(), path.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.fiat_provider_add else R.string.fiat_provider_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.fiat_provider_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.fiat_provider_url)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(path, { path = it }, label = { Text(stringResource(R.string.fiat_provider_path)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (candidate.rejectedAsset) Text(stringResource(R.string.fiat_provider_reject), color = MaterialTheme.colorScheme.error)
                Hint(stringResource(R.string.fiat_provider_help))
                Hint(stringResource(R.string.fiat_provider_gecko))
            }
        },
        confirmButton = { TextButton(enabled = candidate.valid(), onClick = { onSave(candidate) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { onDelete(existing.id) }) { Text(stringResource(R.string.fiat_provider_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun PairDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val normalized = FiatCodes.normalize(code)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fiat_pair_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it }, label = { Text(stringResource(R.string.fiat_pair_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Hint(stringResource(R.string.fiat_pair_help))
            }
        },
        confirmButton = { TextButton(enabled = normalized != null, onClick = { normalized?.let(onSave) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
