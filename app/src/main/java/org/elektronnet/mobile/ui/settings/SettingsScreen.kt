package org.elektronnet.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.BuildConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.policy.PolicySettings

@Composable
fun SettingsScreen(
    settings: PolicySettings,
    onAllowMetered: (Boolean) -> Unit,
    onChargingOnly: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_network_battery), style = MaterialTheme.typography.titleMedium)
        ToggleRow(R.string.settings_mobile_data, R.string.settings_mobile_data_hint, settings.allowMetered, onAllowMetered)
        ToggleRow(R.string.settings_charging_only, R.string.settings_charging_only_hint, settings.chargingOnly, onChargingOnly)
        ToggleRow(R.string.settings_background, R.string.settings_background_hint, settings.backgroundEnabled, onBackground)
        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: Int, hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title))
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
