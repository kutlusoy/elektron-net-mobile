package org.elektronnet.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.service.SigningInfo
import androidx.compose.ui.platform.LocalContext
import org.elektronnet.mobile.service.CoreManifestLoader
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.BuildConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.node.NodeProfile
import org.elektronnet.mobile.policy.PolicySettings
import org.elektronnet.mobile.policy.ScheduleWindow
import org.elektronnet.mobile.ui.theme.ElektronMono
import org.elektronnet.mobile.ui.tools.Hint
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
    biometric: Boolean = false,
    onBiometric: (Boolean) -> Unit = {},
    onPolicy: (PolicySettings) -> Unit = {},
    fastprune: Boolean = true,
    onFastprune: (Boolean) -> Unit = {},
    explorers: List<String> = emptyList(),
    onExplorers: (List<String>) -> Unit = {},
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
        if (settings.allowMetered) {
            ToggleRow(R.string.settings_roaming, R.string.settings_roaming_hint, settings.allowRoaming) { onPolicy(settings.copy(allowRoaming = it)) }
            Text(stringResource(R.string.settings_cap), style = MaterialTheme.typography.labelLarge)
            CAP_CHOICES.forEach { gb ->
                val bytes = gb?.let { it * GIB }
                ChoiceRow(settings.monthlyCellularCapBytes == bytes, { onPolicy(settings.copy(monthlyCellularCapBytes = bytes)) }, if (gb == null) stringResource(R.string.settings_cap_none) else stringResource(R.string.settings_cap_gb, gb), null) {}
            }
        }
        ToggleRow(R.string.settings_night, R.string.settings_night_hint, settings.schedule != null) {
            onPolicy(settings.copy(schedule = if (it) ScheduleWindow(NIGHT_START, NIGHT_END) else null))
        }
        if (settings.backgroundEnabled) {
            Text(stringResource(R.string.settings_profile), style = MaterialTheme.typography.labelLarge)
            NodeProfile.entries.forEach { profile ->
                ChoiceRow(settings.profile == profile, { onPolicy(settings.copy(profile = profile)) }, stringResource(profileLabel(profile)), stringResource(profileHint(profile))) {}
            }
        }
        Text(stringResource(R.string.settings_sharing), style = MaterialTheme.typography.titleMedium)
        ToggleRow(R.string.settings_inbound, R.string.settings_inbound_hint, settings.allowInbound) { onPolicy(settings.copy(allowInbound = it)) }
        ToggleRow(R.string.settings_serve, R.string.settings_serve_hint, settings.serveSnapshots) { onPolicy(settings.copy(serveSnapshots = it)) }
        Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleMedium)
        ToggleRow(R.string.settings_fastprune, R.string.settings_fastprune_hint, fastprune, onFastprune)
        Text(stringResource(R.string.settings_floor), style = MaterialTheme.typography.labelLarge)
        FLOOR_CHOICES.forEach { gb ->
            ChoiceRow(settings.storageFloorBytes == gb * GIB, { onPolicy(settings.copy(storageFloorBytes = gb * GIB)) }, stringResource(R.string.settings_cap_gb, gb), null) {}
        }
        Hint(stringResource(R.string.settings_floor_hint))
        ExplorerSection(explorers, onExplorers)
        Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium)
        ToggleRow(R.string.settings_biometric, R.string.settings_biometric_hint, biometric, onBiometric)
        var notifyIncoming by remember { mutableStateOf(NodeHost.store?.notifyIncoming == true) }
        ToggleRow(R.string.notify_incoming_setting, R.string.notify_incoming_hint, notifyIncoming) {
            notifyIncoming = it
            NodeHost.store?.notifyIncoming = it
        }
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
        AppearanceSection(ui, onUi)
        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        val context = LocalContext.current
        val node = remember { CoreManifestLoader.load(context)?.label() ?: BuildConfig.NODE_VERSION }
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.settings_wallet_version, BuildConfig.MOBILE_VERSION), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.settings_node_version, node), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val manifest = remember { CoreManifestLoader.load(context) }
        manifest?.commit?.let { Text(stringResource(R.string.settings_node_commit, it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall) }
        manifest?.libSha256?.let { Text(stringResource(R.string.settings_node_hash, it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall) }
        val fingerprint = remember { SigningInfo.fingerprint(context) }
        fingerprint?.let { Text(stringResource(R.string.settings_signing, it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall) }
        Text(stringResource(R.string.about_slogan), color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        listOf(
            R.string.about_link_repo to "https://github.com/kutlusoy/elektron-net-mobile",
            R.string.about_link_node to "https://github.com/kutlusoy/elektron-net",
            R.string.about_link_whitepaper to "https://github.com/kutlusoy/elektron-net/blob/main/WHITEPAPER.md",
        ).forEach { (label, url) ->
            androidx.compose.material3.TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }) {
                Text(stringResource(label))
            }
        }
        Text(stringResource(R.string.settings_copyright), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val GIB = 1024L * 1024 * 1024
private val CAP_CHOICES = listOf<Long?>(null, 1L, 2L, 5L, 10L)
private val FLOOR_CHOICES = listOf(1L, 2L, 5L)
private const val NIGHT_START = 22 * 60
private const val NIGHT_END = 6 * 60

private fun profileLabel(profile: NodeProfile): Int = when (profile) {
    NodeProfile.Foreground -> R.string.profile_foreground
    NodeProfile.Balanced -> R.string.profile_balanced
    NodeProfile.AlwaysOn -> R.string.profile_always
}

private fun profileHint(profile: NodeProfile): Int = when (profile) {
    NodeProfile.Foreground -> R.string.profile_foreground_hint
    NodeProfile.Balanced -> R.string.profile_balanced_hint
    NodeProfile.AlwaysOn -> R.string.profile_always_hint
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
    val dark = ui.dark
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
    ThemeMode.Dark -> R.string.settings_mode_dark
    ThemeMode.Light -> R.string.settings_mode_light
}
