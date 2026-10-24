package org.elektronnet.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.theme.NumberState
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.wallet.NumberStyle
import org.elektronnet.mobile.wallet.SeparatorChoice

@Composable
fun NumberFormatSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(R.string.settings_number_group, R.string.settings_number_group_hint, NumberState.grouping) {
            NumberState.grouping = it
            NodeHost.store?.numberGrouping = it
        }
        if (NumberState.grouping) {
            Text(stringResource(R.string.settings_number_group_char), style = MaterialTheme.typography.labelLarge)
            SeparatorPicker(NumberState.groupChoice, thousands = true) {
                NumberState.groupChoice = it
                NodeHost.store?.numberGroupChar = it.key
            }
        }
        Text(stringResource(R.string.settings_number_decimal_char), style = MaterialTheme.typography.labelLarge)
        SeparatorPicker(NumberState.decimalChoice, thousands = false) {
            NumberState.decimalChoice = it
            NodeHost.store?.numberDecimalChar = it.key
        }
        val style = NumberState.style()
        Text(stringResource(R.string.settings_number_example, style.format("1234567.89")), color = MaterialTheme.colorScheme.primary)
        Hint(stringResource(R.string.settings_number_default_hint, NumberStyle.forLocale(Locale.getDefault()).format("1234567.89")))
    }
}

@Composable
private fun SeparatorPicker(current: SeparatorChoice, thousands: Boolean, onPick: (SeparatorChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = if (thousands) SeparatorChoice.entries else SeparatorChoice.entries.filter { it == SeparatorChoice.Default || it == SeparatorChoice.Comma || it == SeparatorChoice.Period }
    Column {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label(current)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { choice -> DropdownMenuItem(text = { Text(label(choice)) }, onClick = { open = false; onPick(choice) }) }
        }
    }
}

@Composable
private fun label(choice: SeparatorChoice): String = stringResource(
    when (choice) {
        SeparatorChoice.Default -> R.string.number_char_default
        SeparatorChoice.Comma -> R.string.number_char_comma
        SeparatorChoice.Period -> R.string.number_char_period
        SeparatorChoice.Space -> R.string.number_char_space
        SeparatorChoice.Apostrophe -> R.string.number_char_apostrophe
    },
)
