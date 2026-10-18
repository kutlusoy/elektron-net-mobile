package org.elektronnet.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import org.elektronnet.mobile.ui.model.EffectSetting
import org.elektronnet.mobile.ui.model.ThemeMode
import org.elektronnet.mobile.ui.theme.Palette
import org.elektronnet.mobile.ui.theme.Palettes
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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.elektronnet.mobile.service.CoreManifestLoader
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.BuildConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.policy.PolicySettings
import org.elektronnet.mobile.ui.model.UiSettings

@Composable
fun SettingsScreen(
    settings: PolicySettings,
    ui: UiSettings,
    onUi: (UiSettings) -> Unit,
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
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
        AppearanceSection(ui, onUi)
        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        val context = LocalContext.current
        val node = remember { CoreManifestLoader.load(context)?.label() ?: BuildConfig.NODE_VERSION }
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.settings_wallet_version, BuildConfig.MOBILE_VERSION), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.settings_node_version, node), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.settings_copyright), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun AppearanceSection(ui: UiSettings, onUi: (UiSettings) -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = ui.isDark(systemDark)
    val selectedKey = ui.themeKey ?: BuildConfig.DEFAULT_THEME
    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
    Palettes.all.forEach { palette ->
        val tokens = if (dark) palette.dark else palette.light
        ChoiceRow(
            selected = palette.key == selectedKey,
            onSelect = { onUi(ui.copy(themeKey = palette.key)) },
            label = palette.name,
            hint = null,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(tokens.bg, tokens.surface, tokens.accent, tokens.accent2).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(color, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
            }
        }
    }
    ThemePreview(Palettes.byKey(selectedKey), dark)
    Text(stringResource(R.string.settings_mode), style = MaterialTheme.typography.labelLarge)
    ThemeMode.entries.forEach { mode ->
        ChoiceRow(ui.mode == mode, { onUi(ui.copy(mode = mode)) }, stringResource(modeLabel(mode)), null) {}
    }
    Text(stringResource(R.string.settings_effects), style = MaterialTheme.typography.labelLarge)
    EffectSetting.entries.forEach { effect ->
        ChoiceRow(ui.effects == effect, { onUi(ui.copy(effects = effect)) }, stringResource(effectLabel(effect)), stringResource(effectHint(effect))) {}
    }
    Text(
        stringResource(R.string.settings_effects_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, onSelect: () -> Unit, label: String, hint: String?, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun ThemePreview(palette: Palette, dark: Boolean) {
    val tokens = if (dark) palette.dark else palette.light
    val status = if (dark) Palettes.darkStatus else Palettes.lightStatus
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.bg, RoundedCornerShape(16.dp))
            .border(1.dp, tokens.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.surface, RoundedCornerShape(12.dp))
                .border(1.dp, tokens.border, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(stringResource(R.string.balance_label), color = tokens.muted, style = MaterialTheme.typography.bodySmall)
            Text("12.5 ELEK", color = tokens.text, style = MaterialTheme.typography.headlineSmall)
        }
        Box(
            modifier = Modifier
                .background(tokens.accent, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) { Text(stringResource(R.string.action_receive), color = tokens.onAccent) }
        Text(stringResource(R.string.settings_preview_warning), color = status.warning)
        Text(stringResource(R.string.settings_preview_link), color = tokens.link)
    }
}

private fun modeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.settings_mode_system
    ThemeMode.Dark -> R.string.settings_mode_dark
    ThemeMode.Light -> R.string.settings_mode_light
}

private fun effectLabel(effect: EffectSetting): Int = when (effect) {
    EffectSetting.Off -> R.string.settings_effects_off
    EffectSetting.Subtle -> R.string.settings_effects_subtle
    EffectSetting.Full -> R.string.settings_effects_full
}

private fun effectHint(effect: EffectSetting): Int = when (effect) {
    EffectSetting.Off -> R.string.settings_effects_off_hint
    EffectSetting.Subtle -> R.string.settings_effects_subtle_hint
    EffectSetting.Full -> R.string.settings_effects_full_hint
}
