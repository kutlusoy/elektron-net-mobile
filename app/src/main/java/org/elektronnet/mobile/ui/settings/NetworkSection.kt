package org.elektronnet.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.node.ExitPolicyKind
import org.elektronnet.mobile.node.I2pSettings
import org.elektronnet.mobile.node.ServiceState
import org.elektronnet.mobile.node.TorRole
import org.elektronnet.mobile.node.TorRouting
import org.elektronnet.mobile.node.TorSettings
import org.elektronnet.mobile.policy.PolicySettings
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.tools.Hint

private val TOR_BANDWIDTH = listOf(75, 128, 256, 512, 1024, 2048, 4096, 8192)
private val TOR_LIMIT_GB = listOf(0, 10, 25, 50, 100, 250, 500, 1000)
private val I2P_BANDWIDTH = listOf(32, 64, 128, 256, 512, 1024, 2048, 4096)
private val I2P_SHARE = listOf(10, 25, 50, 75, 100)

private fun indexAtLeast(steps: List<Int>, value: Int): Int = steps.indexOfFirst { it >= value }.let { if (it < 0) steps.lastIndex else it }

@Composable
private fun statusText(state: ServiceState?, available: Boolean): String = stringResource(
    when {
        !available -> R.string.net_status_missing
        state == ServiceState.Running -> R.string.net_status_running
        state == ServiceState.Starting -> R.string.net_status_starting
        state is ServiceState.Failed -> R.string.net_status_failed
        else -> R.string.net_status_idle
    },
)

@Composable
fun TorSettingsCard(settings: PolicySettings, onPolicy: (PolicySettings) -> Unit) {
    val tor = settings.network.tor
    fun update(next: TorSettings) = onPolicy(settings.copy(network = settings.network.copy(tor = next)))
    var confirmExit by remember { mutableStateOf(false) }
    SettingsCard(R.string.net_tor_title) {
        ToggleRow(R.string.net_tor_switch, R.string.net_tor_hint, tor.enabled) { update(tor.copy(enabled = it)) }
        if (tor.enabled) {
            val services = NodeHost.networkServices
            Text(
                stringResource(R.string.net_status, statusText(services?.torState, services?.torAvailable ?: false)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.net_tor_routing), style = MaterialTheme.typography.labelLarge)
            ChoiceRow(tor.routing == TorRouting.Additional, { update(tor.copy(routing = TorRouting.Additional)) }, stringResource(R.string.net_tor_additional), stringResource(R.string.net_tor_additional_hint)) {}
            ChoiceRow(tor.routing == TorRouting.AllTraffic, { update(tor.copy(routing = TorRouting.AllTraffic)) }, stringResource(R.string.net_tor_all), stringResource(R.string.net_tor_all_hint)) {}
            Text(stringResource(R.string.net_tor_role), style = MaterialTheme.typography.labelLarge)
            ChoiceRow(tor.role == TorRole.Client, { update(tor.copy(role = TorRole.Client)) }, stringResource(R.string.net_tor_client), stringResource(R.string.net_tor_client_hint)) {}
            ChoiceRow(tor.role == TorRole.Relay, { update(tor.copy(role = TorRole.Relay)) }, stringResource(R.string.net_tor_relay), stringResource(R.string.net_tor_relay_hint)) {}
            ChoiceRow(tor.role == TorRole.Exit, { if (tor.role != TorRole.Exit) confirmExit = true }, stringResource(R.string.net_tor_exit), stringResource(R.string.net_tor_exit_hint)) {}
            if (tor.role != TorRole.Client) RelayOptions(tor, ::update)
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.net_tor_exit_confirm_title)) },
            text = { Text(stringResource(R.string.net_tor_exit_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmExit = false; update(tor.copy(role = TorRole.Exit)) }) { Text(stringResource(R.string.net_tor_exit_confirm_yes)) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun RelayOptions(tor: TorSettings, update: (TorSettings) -> Unit) {
    val context = LocalContext.current
    Hint(stringResource(R.string.net_tor_relay_note))
    val bandwidthIndex = indexAtLeast(TOR_BANDWIDTH, tor.bandwidthKbps)
    StepSlider(
        title = R.string.net_tor_bandwidth,
        steps = TOR_BANDWIDTH.size,
        index = bandwidthIndex,
        label = stringResource(R.string.net_kbps, TOR_BANDWIDTH[bandwidthIndex]),
        labelFor = { i -> context.getString(R.string.net_kbps, TOR_BANDWIDTH[i]) },
        onCommit = { i -> update(tor.copy(bandwidthKbps = TOR_BANDWIDTH[i])) },
    )
    val limitIndex = indexAtLeast(TOR_LIMIT_GB, tor.monthlyLimitGb)
    StepSlider(
        title = R.string.net_tor_limit,
        steps = TOR_LIMIT_GB.size,
        index = limitIndex,
        label = TOR_LIMIT_GB[limitIndex].let { if (it == 0) stringResource(R.string.settings_cap_none) else stringResource(R.string.settings_cap_gb, it) },
        labelFor = { i -> TOR_LIMIT_GB[i].let { if (it == 0) context.getString(R.string.settings_cap_none) else context.getString(R.string.settings_cap_gb, it) } },
        onCommit = { i -> update(tor.copy(monthlyLimitGb = TOR_LIMIT_GB[i])) },
    )
    if (tor.role == TorRole.Exit) {
        Text(stringResource(R.string.net_tor_exit_policy), style = MaterialTheme.typography.labelLarge)
        ChoiceRow(tor.exitPolicy == ExitPolicyKind.Reduced, { update(tor.copy(exitPolicy = ExitPolicyKind.Reduced)) }, stringResource(R.string.net_tor_policy_reduced), stringResource(R.string.net_tor_policy_reduced_hint)) {}
        ChoiceRow(tor.exitPolicy == ExitPolicyKind.Web, { update(tor.copy(exitPolicy = ExitPolicyKind.Web)) }, stringResource(R.string.net_tor_policy_web), stringResource(R.string.net_tor_policy_web_hint)) {}
    }
    var nickname by remember(tor.nickname) { mutableStateOf(tor.nickname) }
    var contact by remember(tor.contact) { mutableStateOf(tor.contact) }
    var orPort by remember(tor.orPort) { mutableStateOf(tor.orPort.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it.filter { c -> c.isLetterOrDigit() && c.code < 128 }.take(TorSettings.MAX_NICKNAME) },
            label = { Text(stringResource(R.string.net_tor_nickname)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = contact,
            onValueChange = { contact = it.take(TorSettings.MAX_CONTACT) },
            label = { Text(stringResource(R.string.net_tor_contact)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = orPort,
            onValueChange = { orPort = it.filter(Char::isDigit).take(5) },
            label = { Text(stringResource(R.string.net_tor_orport)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(stringResource(R.string.net_tor_orport_hint))
        Button(
            onClick = { update(tor.copy(nickname = nickname, contact = contact, orPort = orPort.toIntOrNull() ?: TorSettings.DEFAULT_OR_PORT)) },
            enabled = nickname != tor.nickname || contact != tor.contact || orPort != tor.orPort.toString(),
        ) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
fun I2pSettingsCard(settings: PolicySettings, onPolicy: (PolicySettings) -> Unit) {
    val i2p = settings.network.i2p
    val context = LocalContext.current
    fun update(next: I2pSettings) = onPolicy(settings.copy(network = settings.network.copy(i2p = next)))
    SettingsCard(R.string.net_i2p_title) {
        ToggleRow(R.string.net_i2p_switch, R.string.net_i2p_hint, i2p.enabled) { update(i2p.copy(enabled = it)) }
        if (i2p.enabled) {
            val services = NodeHost.networkServices
            Text(
                stringResource(R.string.net_status, statusText(services?.i2pState, services?.i2pAvailable ?: false)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleRow(R.string.net_i2p_incoming, R.string.net_i2p_incoming_hint, i2p.acceptIncoming) { update(i2p.copy(acceptIncoming = it)) }
            ToggleRow(R.string.net_i2p_transit, R.string.net_i2p_transit_hint, i2p.transit) { update(i2p.copy(transit = it)) }
            val bandwidthIndex = indexAtLeast(I2P_BANDWIDTH, i2p.bandwidthKbps)
            StepSlider(
                title = R.string.net_i2p_bandwidth,
                steps = I2P_BANDWIDTH.size,
                index = bandwidthIndex,
                label = stringResource(R.string.net_kbps, I2P_BANDWIDTH[bandwidthIndex]),
                labelFor = { i -> context.getString(R.string.net_kbps, I2P_BANDWIDTH[i]) },
                onCommit = { i -> update(i2p.copy(bandwidthKbps = I2P_BANDWIDTH[i])) },
            )
            if (i2p.transit) {
                val shareIndex = indexAtLeast(I2P_SHARE, i2p.sharePercent)
                StepSlider(
                    title = R.string.net_i2p_share,
                    steps = I2P_SHARE.size,
                    index = shareIndex,
                    label = stringResource(R.string.net_percent, I2P_SHARE[shareIndex]),
                    labelFor = { i -> context.getString(R.string.net_percent, I2P_SHARE[i]) },
                    onCommit = { i -> update(i2p.copy(sharePercent = I2P_SHARE[i])) },
                )
            }
            Hint(stringResource(R.string.net_i2p_note))
        }
    }
}
