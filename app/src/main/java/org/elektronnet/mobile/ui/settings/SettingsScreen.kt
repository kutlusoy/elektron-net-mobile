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
import org.elektronnet.mobile.ui.theme.UpdateState
import org.elektronnet.mobile.ui.theme.UpdateStatus
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.ui.theme.UnitState
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.AmountUnit
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import org.elektronnet.mobile.service.AppLanguage
import org.elektronnet.mobile.ui.GlassCard
import org.elektronnet.mobile.ui.ScreenshotMode
import org.elektronnet.mobile.ui.model.AppLockModel
import org.elektronnet.mobile.ui.model.LanguageChoices
import org.elektronnet.mobile.ui.security.AppLockState
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
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard(R.string.settings_network_battery) {
            ToggleRow(R.string.settings_mobile_data, R.string.settings_mobile_data_hint, settings.allowMetered, onAllowMetered)
            if (settings.allowMetered) {
                ToggleRow(R.string.settings_roaming, R.string.settings_roaming_hint, settings.allowRoaming) { onPolicy(settings.copy(allowRoaming = it)) }
                val capBytes = settings.monthlyCellularCapBytes
                val capIndex = if (capBytes == null) CAP_CHOICES.lastIndex else CAP_CHOICES.indexOfFirst { gb -> gb != null && gb * GIB >= capBytes }.let { if (it < 0) CAP_CHOICES.lastIndex else it }
                StepSlider(
                    title = R.string.settings_cap,
                    steps = CAP_CHOICES.size,
                    index = capIndex,
                    label = CAP_CHOICES[capIndex]?.let { stringResource(R.string.settings_cap_gb, it) } ?: stringResource(R.string.settings_cap_none),
                    labelFor = { i -> CAP_CHOICES[i]?.let { context.getString(R.string.settings_cap_gb, it) } ?: context.getString(R.string.settings_cap_none) },
                    onCommit = { i -> onPolicy(settings.copy(monthlyCellularCapBytes = CAP_CHOICES[i]?.let { it * GIB })) },
                )
            }
            ToggleRow(R.string.settings_charging_only, R.string.settings_charging_only_hint, settings.chargingOnly, onChargingOnly)
            ToggleRow(R.string.settings_background, R.string.settings_background_hint, settings.backgroundEnabled, onBackground)
            ToggleRow(R.string.settings_night, R.string.settings_night_hint, settings.schedule != null) {
                onPolicy(settings.copy(schedule = if (it) ScheduleWindow(NIGHT_START, NIGHT_END) else null))
            }
            if (settings.backgroundEnabled) {
                Text(stringResource(R.string.settings_profile), style = MaterialTheme.typography.labelLarge)
                NodeProfile.entries.forEach { profile ->
                    ChoiceRow(settings.profile == profile, { onPolicy(settings.copy(profile = profile)) }, stringResource(profileLabel(profile)), stringResource(profileHint(profile))) {}
                }
            }
        }
        SettingsCard(R.string.settings_sharing) {
            ToggleRow(R.string.settings_inbound, R.string.settings_inbound_hint, settings.allowInbound) { onPolicy(settings.copy(allowInbound = it)) }
            ToggleRow(R.string.settings_serve, R.string.settings_serve_hint, settings.serveSnapshots) { onPolicy(settings.copy(serveSnapshots = it)) }
        }
        TorSettingsCard(settings, onPolicy)
        I2pSettingsCard(settings, onPolicy)
        SettingsCard(R.string.settings_storage) {
            ToggleRow(R.string.settings_fastprune, R.string.settings_fastprune_hint, fastprune, onFastprune)
            val floorGb = settings.storageFloorBytes / GIB
            val floorIndex = FLOOR_CHOICES.indexOfFirst { it >= floorGb }.let { if (it < 0) FLOOR_CHOICES.lastIndex else it }
            StepSlider(
                title = R.string.settings_floor,
                steps = FLOOR_CHOICES.size,
                index = floorIndex,
                label = stringResource(R.string.settings_cap_gb, FLOOR_CHOICES[floorIndex]),
                labelFor = { i -> context.getString(R.string.settings_cap_gb, FLOOR_CHOICES[i]) },
                onCommit = { i -> onPolicy(settings.copy(storageFloorBytes = FLOOR_CHOICES[i] * GIB)) },
            )
            Hint(stringResource(R.string.settings_floor_hint))
            LogSettingsSection()
        }
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { ExplorerSection(explorers, onExplorers) }
        }
        SettingsCard(R.string.settings_security) {
            ToggleRow(R.string.settings_biometric, R.string.settings_biometric_hint, biometric, onBiometric)
            if (biometric) {
                val grace = AppLockState.graceChoice
                val lockIndex = AppLockModel.CHOICES_MS.indexOfFirst { it >= grace }.let { if (it < 0) AppLockModel.CHOICES_MS.lastIndex else it }
                StepSlider(
                    title = R.string.settings_lock_delay,
                    steps = AppLockModel.CHOICES_MS.size,
                    index = lockIndex,
                    label = lockLabel(AppLockModel.CHOICES_MS[lockIndex]),
                    labelFor = { i -> lockLabel(AppLockModel.CHOICES_MS[i], context) },
                    onCommit = { i ->
                        NodeHost.store?.lockGraceMs = AppLockModel.CHOICES_MS[i]
                        AppLockState.applyGrace(AppLockModel.CHOICES_MS[i])
                    },
                )
                Hint(stringResource(R.string.settings_lock_delay_hint))
            }
            var spendReused by remember { mutableStateOf(NodeHost.store?.spendReused != false) }
            ToggleRow(R.string.settings_spend_reused, R.string.settings_spend_reused_hint, spendReused) {
                spendReused = it
                NodeHost.store?.spendReused = it
            }
            var notifyIncoming by remember { mutableStateOf(NodeHost.store?.notifyIncoming == true) }
            ToggleRow(R.string.notify_incoming_setting, R.string.notify_incoming_hint, notifyIncoming) {
                notifyIncoming = it
                NodeHost.store?.notifyIncoming = it
            }
            ToggleRow(R.string.settings_screenshots, R.string.settings_screenshots_hint, ScreenshotMode.allowed) { ScreenshotMode.allowed = it }
        }
        SettingsCard(R.string.settings_language) { LanguagePicker() }
        SettingsCard(R.string.settings_fiat) { FiatSection() }
        SettingsCard(R.string.settings_units) {
            UnitPicker()
            NumberFormatSection()
        }
        SettingsCard(R.string.settings_appearance) { AppearanceSection(ui, onUi) }
        SettingsCard(R.string.settings_about) {
            val node = remember { CoreManifestLoader.load(context)?.label() ?: BuildConfig.NODE_VERSION }
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = muted)
            Text(stringResource(R.string.settings_wallet_version, BuildConfig.MOBILE_VERSION), color = muted)
            Text(stringResource(R.string.settings_node_version, node), color = muted)
            val manifest = remember { CoreManifestLoader.load(context) }
            manifest?.commit?.let { Text(stringResource(R.string.settings_node_commit, it), color = muted, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall) }
            manifest?.libSha256?.let { Text(stringResource(R.string.settings_node_hash, it), color = muted, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall) }
            val fingerprint = remember { SigningInfo.fingerprint(context) }
            fingerprint?.let { Text(stringResource(R.string.settings_signing, it), color = muted, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.about_slogan), color = muted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            listOf(
                R.string.about_link_repo to "https://github.com/kutlusoy/elektron-net-mobile",
                R.string.about_link_node to "https://github.com/kutlusoy/elektron-net",
                R.string.about_link_whitepaper to "https://github.com/kutlusoy/elektron-net/blob/main/WHITEPAPER.md",
            ).forEach { (label, url) ->
                androidx.compose.material3.TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }) {
                    Text(stringResource(label))
                }
            }
            Text(stringResource(R.string.about_license), color = muted)
            Text(stringResource(R.string.about_developers), color = muted)
            Text(stringResource(R.string.settings_copyright), color = muted)
        }
        SettingsCard(R.string.update_title) { UpdateChecker() }
    }
}

@Composable
private fun UnitPicker() {
    val saved = UnitState.current
    var position by remember(saved) { mutableStateOf(saved.ordinal.toFloat()) }
    val shown = AmountUnit.entries[position.roundToInt().coerceIn(0, AmountUnit.entries.lastIndex)]
    Text(stringResource(unitLabel(shown)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Slider(
        value = position,
        onValueChange = { position = it },
        onValueChangeFinished = {
            val chosen = AmountUnit.entries[position.roundToInt().coerceIn(0, AmountUnit.entries.lastIndex)]
            UnitState.current = chosen
            NodeHost.store?.amountUnit = chosen
        },
        valueRange = 0f..AmountUnit.entries.lastIndex.toFloat(),
        steps = AmountUnit.entries.size - 2,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AmountUnit.entries.forEach { Text(it.symbol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Text(
        "1 ELEK = " + Amount.numberStyle.format(Amount.formatIn(shown, Amount.LEP_PER_ELEK)) + " " + shown.symbol,
        style = MaterialTheme.typography.headlineSmall,
        fontFamily = ElektronMono,
    )
    Text(stringResource(R.string.units_rates), fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
    Hint(stringResource(R.string.units_hint))
}

private fun unitLabel(unit: AmountUnit): Int = when (unit) {
    AmountUnit.Elek -> R.string.unit_elek
    AmountUnit.MilliElek -> R.string.unit_melek
    AmountUnit.Bit -> R.string.unit_bit
    AmountUnit.Lepton -> R.string.unit_lep
}

@Composable
private fun UpdateChecker() {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val busy = UpdateState.status == UpdateStatus.Checking
    Hint(stringResource(R.string.update_hint))
    Hint(stringResource(if (UpdateState.testBuild) R.string.update_channel_test else R.string.update_channel_release))
    ToggleRow(R.string.update_auto, R.string.update_auto_hint, UpdateState.auto) { UpdateState.setAuto(it) }
    androidx.compose.material3.OutlinedButton(
        enabled = !busy,
        onClick = { scope.launch { UpdateState.check() } },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(if (busy) R.string.update_checking else R.string.update_check)) }
    if (UpdateState.status == UpdateStatus.Failed) Text(stringResource(R.string.update_failed), color = MaterialTheme.colorScheme.error)
    val offer = UpdateState.offer
    when {
        offer != null -> UpdateOffer(offer)
        UpdateState.status == UpdateStatus.UpToDate -> Text(stringResource(R.string.update_latest))
    }
}

@Composable
internal fun SettingsCard(title: Int, content: @Composable () -> Unit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun StepSlider(title: Int, steps: Int, index: Int, label: String, labelFor: (Int) -> String, onCommit: (Int) -> Unit) {
    var position by remember(index) { mutableStateOf(index.toFloat()) }
    val shown = position.roundToInt().coerceIn(0, steps - 1)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(title), modifier = Modifier.weight(1f))
            Text(if (shown == index) label else labelFor(shown), color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = position,
            onValueChange = { position = it },
            // the callback can run before the last value change was composed, so it reads the current position, not `shown`
            onValueChangeFinished = { onCommit(position.roundToInt().coerceIn(0, steps - 1)) },
            valueRange = 0f..(steps - 1).toFloat(),
            steps = (steps - 2).coerceAtLeast(0),
        )
    }
}

@Composable
private fun lockLabel(ms: Long): String = lockLabel(ms, LocalContext.current)

private fun lockLabel(ms: Long, context: android.content.Context): String = when {
    ms == 0L -> context.getString(R.string.settings_lock_now)
    ms < 60_000L -> context.getString(R.string.settings_seconds, (ms / 1000).toInt())
    else -> context.getString(R.string.settings_minutes, (ms / 60_000).toInt())
}

@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = AppLanguage.stored(context)
    val name = LanguageChoices.choices.firstOrNull { it.tag == current }?.nativeName ?: stringResource(R.string.settings_language_system)
    Box {
        androidx.compose.material3.OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(name + "  \u25be") }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_language_system)) },
                onClick = { open = false; applyLanguage(context, null) },
            )
            LanguageChoices.choices.forEach { choice ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(choice.nativeName) }, onClick = { open = false; applyLanguage(context, choice.tag) })
            }
        }
    }
}

private fun applyLanguage(context: android.content.Context, tag: String?) {
    if (AppLanguage.stored(context) == tag) return
    AppLanguage.store(context, tag)
    (context as? android.app.Activity)?.recreate()
}

private const val GIB = 1024L * 1024 * 1024
private val CAP_CHOICES = listOf<Long?>(1L, 2L, 5L, 10L, 20L, 50L, null)
private val FLOOR_CHOICES = listOf(1L, 2L, 3L, 5L, 10L, 20L)
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
internal fun ToggleRow(title: Int, hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun ChoiceRow(selected: Boolean, onSelect: () -> Unit, label: String, hint: String?, trailing: @Composable () -> Unit) {
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
