package org.elektronnet.mobile.ui.wallets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.GlassCard
import org.elektronnet.mobile.ui.theme.ElektronMono
import org.elektronnet.mobile.ui.tools.ErrorText
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.ui.tools.ToolPage
import org.elektronnet.mobile.ui.tools.copyText
import org.elektronnet.mobile.wallet.AddressRow
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.Balances
import org.elektronnet.mobile.wallet.Coin
import org.elektronnet.mobile.wallet.Descriptors
import org.elektronnet.mobile.wallet.OwnWallet

@Composable
fun walletTitle(wallet: OwnWallet?): String = wallet?.label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.wallet_default_name)

@Composable
fun WalletSwitcher(
    onDetails: () -> Unit,
    onCoins: () -> Unit,
    onMessages: () -> Unit,
    onSweep: () -> Unit,
    onTools: () -> Unit,
    onWallets: () -> Unit,
    onContacts: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val active = NodeHost.wallets.firstOrNull { it.name == NodeHost.activeWallet }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { open = true }) {
            Text(walletTitle(active) + "  ▾", style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable fun item(label: Int, action: () -> Unit) = DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { open = false; action() })
            item(R.string.wallet_menu_details, onDetails)
            item(R.string.wallet_menu_coins, onCoins)
            item(R.string.msg_title, onMessages)
            item(R.string.sweep_title, onSweep)
            item(R.string.contacts_title, onContacts)
            item(R.string.wallet_menu_tools, onTools)
            HorizontalDivider()
            item(R.string.wallet_menu_others, onWallets)
        }
    }
}

@Composable
fun WalletDetailsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val wallet = NodeHost.wallets.firstOrNull { it.name == NodeHost.activeWallet }
    var balances by remember { mutableStateOf<Balances?>(null) }
    var name by remember(wallet) { mutableStateOf(wallet?.label.orEmpty()) }
    LaunchedEffect(NodeHost.activeWallet) {
        balances = withContext(Dispatchers.IO) { runCatching { NodeHost.walletReader()?.balances() }.getOrNull() }
    }
    val coinType = if (ChainConfig.isTestnet) Descriptors.COIN_TYPE_TESTNET else Descriptors.COIN_TYPE_ELEKTRON
    ToolPage(stringResource(R.string.details_title), onBack) {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_LABEL) },
                    label = { Text(stringResource(R.string.wallets_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { NodeHost.renameWallet(NodeHost.activeWallet, name) }, enabled = name.trim() != wallet?.label.orEmpty()) {
                    Text(stringResource(R.string.action_save))
                }
                InfoRow(stringResource(R.string.details_id), NodeHost.activeWallet, mono = true)
                balances?.let {
                    InfoRow(stringResource(R.string.details_balance), Amount.format(it.trustedLep) + " " + stringResource(R.string.balance_unit))
                    InfoRow(stringResource(R.string.details_pending), Amount.format(it.pendingLep) + " " + stringResource(R.string.balance_unit))
                    InfoRow(stringResource(R.string.details_immature), Amount.format(it.immatureLep) + " " + stringResource(R.string.balance_unit))
                    if (it.usedLep > 0) {
                        InfoRow(stringResource(R.string.details_used), Amount.format(it.usedLep) + " " + stringResource(R.string.balance_unit))
                        Hint(stringResource(R.string.details_used_hint))
                    }
                }
                InfoRow(stringResource(R.string.details_derivation), stringResource(R.string.details_derivation_value, coinType))
            }
        }
        var rescanning by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { rescanning = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rescan_action)) }
        Hint(stringResource(R.string.rescan_hint))
        if (rescanning) RescanDialog(onDismiss = { rescanning = false })
        ExportSection()
        Hint(stringResource(R.string.details_noreuse))
        Hint(stringResource(R.string.details_hint))
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = if (mono) ElektronMono else null, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun CoinsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var coins by remember { mutableStateOf<List<Coin>?>(null) }
    var addresses by remember { mutableStateOf<List<AddressRow>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var labelFor by remember { mutableStateOf<AddressRow?>(null) }
    LaunchedEffect(NodeHost.activeWallet, tick) {
        val result = withContext(Dispatchers.IO) {
            runCatching { NodeHost.coinControl()?.let { it.coins() to it.addresses() } }.getOrNull()
        }
        failed = result == null
        if (result != null) {
            coins = result.first
            addresses = result.second
        }
    }
    ToolPage(stringResource(R.string.coins_title), onBack) {
        TabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.coins_tab_coins)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.coins_tab_addresses)) })
        }
        if (failed) ErrorText(stringResource(R.string.coins_unavailable))
        if (tab == 0) {
            val list = coins.orEmpty()
            val unit = stringResource(R.string.balance_unit)
            Text(
                stringResource(R.string.coins_summary, Amount.format(list.filter { !it.frozen }.sumOf { it.amountLep }) + " " + unit, Amount.format(list.filter { it.frozen }.sumOf { it.amountLep }) + " " + unit),
                style = MaterialTheme.typography.titleMedium,
            )
            Hint(stringResource(R.string.coins_frozen_hint))
            if (coins != null && list.isEmpty()) Text(stringResource(R.string.coins_empty))
            list.forEach { coin ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(Amount.format(coin.amountLep) + " " + unit, style = MaterialTheme.typography.titleMedium)
                            coin.address?.let {
                                Text(it, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { copyText(context, "address", it) })
                            }
                            Hint(
                                if (coin.confirmations <= 0) stringResource(R.string.coins_unconfirmed) else stringResource(R.string.coins_conf, coin.confirmations),
                            )
                            if (coin.reused) Text(stringResource(R.string.coins_reused), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(
                                checked = coin.frozen,
                                onCheckedChange = { frozen ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) { runCatching { NodeHost.coinControl()?.setFrozen(listOf(coin), frozen) } }
                                        tick++
                                    }
                                },
                            )
                            Text(stringResource(R.string.coins_frozen), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else {
            val rows = addresses.orEmpty()
            if (addresses != null && rows.isEmpty()) Text(stringResource(R.string.addr_empty))
            rows.forEach { row ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).clickable { copyText(context, "address", row.address) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.address, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
                            row.label?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                            Hint(stringResource(R.string.addr_received, Amount.format(row.receivedLep) + " " + stringResource(R.string.balance_unit)) + "  ·  " + stringResource(R.string.addr_tx, row.txCount))
                        }
                        TextButton(onClick = { labelFor = row }) { Text(stringResource(R.string.addr_label)) }
                    }
                }
            }
            Hint(stringResource(R.string.addr_copy_hint))
        }
    }
    labelFor?.let { row ->
        LabelDialog(
            row = row,
            onSave = { text ->
                labelFor = null
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { NodeHost.coinControl()?.setLabel(row.address, text) } }
                    tick++
                }
            },
            onDismiss = { labelFor = null },
        )
    }
}

@Composable
private fun LabelDialog(row: AddressRow, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(row) { mutableStateOf(row.label.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.addr_label)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(MAX_LABEL * 2) }, singleLine = true, label = { Text(stringResource(R.string.addr_label_hint)) }) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun WalletsScreen(onBack: () -> Unit, onAdd: () -> Unit) {
    BackHandler(onBack = onBack)
    var renaming by remember { mutableStateOf<OwnWallet?>(null) }
    var removing by remember { mutableStateOf<OwnWallet?>(null) }
    ToolPage(stringResource(R.string.wallets_title), onBack) {
        NodeHost.wallets.forEach { wallet ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = wallet.name == NodeHost.activeWallet, onClick = { NodeHost.switchWallet(wallet.name) })
                    Column(modifier = Modifier.weight(1f).clickable { NodeHost.switchWallet(wallet.name) }) {
                        Text(walletTitle(wallet), style = MaterialTheme.typography.titleMedium)
                        Hint(wallet.name)
                    }
                    TextButton(onClick = { renaming = wallet }) { Text(stringResource(R.string.wallets_rename)) }
                    TextButton(onClick = { removing = wallet }) { Text(stringResource(R.string.wallets_remove), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wallets_add)) }
        Hint(stringResource(R.string.wallets_hint))
    }
    removing?.let { wallet -> RemoveWalletDialog(wallet, onDismiss = { removing = null }) }
    renaming?.let { wallet ->
        var text by remember(wallet) { mutableStateOf(wallet.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.wallets_rename)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(MAX_LABEL) }, singleLine = true, label = { Text(stringResource(R.string.wallets_name_label)) })
            },
            confirmButton = {
                TextButton(onClick = {
                    NodeHost.renameWallet(wallet.name, text)
                    renaming = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { OutlinedButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private const val MAX_LABEL = 32
private const val EXPORT_LIMIT = 1000

@Composable
fun CoinPickerDialog(wallet: String, selected: List<Coin>, onDone: (List<Coin>) -> Unit, onDismiss: () -> Unit) {
    var coins by remember { mutableStateOf<List<Coin>?>(null) }
    var chosen by remember { mutableStateOf(selected.map { it.id }.toSet()) }
    LaunchedEffect(wallet) {
        coins = withContext(Dispatchers.IO) { runCatching { NodeHost.coinControl(wallet)?.coins()?.filter { !it.frozen } }.getOrNull() }.orEmpty()
    }
    val unit = stringResource(R.string.balance_unit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.coinpick_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Hint(stringResource(R.string.coinpick_hint))
                val list = coins
                if (list == null) {
                    androidx.compose.material3.CircularProgressIndicator()
                } else if (list.isEmpty()) {
                    Text(stringResource(R.string.coins_empty))
                }
                list.orEmpty().forEach { coin ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { chosen = if (coin.id in chosen) chosen - coin.id else chosen + coin.id },
                    ) {
                        androidx.compose.material3.Checkbox(checked = coin.id in chosen, onCheckedChange = null)
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(Amount.format(coin.amountLep) + " " + unit)
                            coin.address?.let { Text(it.take(10) + "..." + it.takeLast(6), fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(coins.orEmpty().filter { it.id in chosen }) }) { Text(stringResource(R.string.coinpick_use)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RemoveWalletDialog(wallet: OwnWallet, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<org.elektronnet.mobile.wallet.RemoveFailure?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.wallets_remove_title, walletTitle(wallet))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.wallets_remove_body))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { confirmed = !confirmed }) {
                    androidx.compose.material3.Checkbox(checked = confirmed, onCheckedChange = null)
                    Text(stringResource(R.string.wallets_remove_confirm), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.fr_pass_label)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                failure?.let {
                    ErrorText(
                        stringResource(
                            when (it) {
                                org.elektronnet.mobile.wallet.RemoveFailure.WrongPassphrase -> R.string.send_err_passphrase
                                org.elektronnet.mobile.wallet.RemoveFailure.NodeNotReady -> R.string.fr_err_node
                                org.elektronnet.mobile.wallet.RemoveFailure.Unknown -> R.string.wallets_remove_failed
                            },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmed && passphrase.isNotEmpty() && !busy,
                onClick = {
                    val pass = passphrase
                    passphrase = ""
                    failure = null
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { NodeHost.removeWallet(wallet.name, pass) }.getOrDefault(org.elektronnet.mobile.wallet.RemoveFailure.Unknown) }
                        busy = false
                        if (result == null) onDismiss() else failure = result
                    }
                },
            ) { Text(stringResource(R.string.wallets_remove), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RescanDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<org.elektronnet.mobile.wallet.RescanOutcome?>(null) }
    val synced = NodeHost.latest?.headline is org.elektronnet.mobile.runtime.Headline.Synchronized
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.rescan_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val outcome = result
                when {
                    outcome is org.elektronnet.mobile.wallet.RescanOutcome.Full -> Text(stringResource(R.string.rescan_done_full))
                    outcome is org.elektronnet.mobile.wallet.RescanOutcome.Partial -> Text(stringResource(R.string.rescan_done_partial))
                    else -> {
                        Text(stringResource(if (synced) R.string.rescan_body else R.string.rescan_needs_sync))
                        if (busy) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text(stringResource(R.string.fr_pass_label)) },
                            singleLine = true,
                            enabled = synced && !busy,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (outcome is org.elektronnet.mobile.wallet.RescanOutcome.Failed) {
                            ErrorText(
                                stringResource(if (outcome.reason == org.elektronnet.mobile.wallet.BootstrapFailure.WrongPassphrase) R.string.send_err_passphrase else R.string.rescan_failed),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result is org.elektronnet.mobile.wallet.RescanOutcome.Full || result is org.elektronnet.mobile.wallet.RescanOutcome.Partial) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            } else {
                TextButton(
                    enabled = synced && passphrase.isNotEmpty() && !busy,
                    onClick = {
                        val pass = passphrase
                        passphrase = ""
                        busy = true
                        scope.launch {
                            result = withContext(Dispatchers.IO) {
                                runCatching { NodeHost.rescanActiveWallet(pass) }.getOrDefault(org.elektronnet.mobile.wallet.RescanOutcome.Failed(org.elektronnet.mobile.wallet.BootstrapFailure.Unknown))
                            }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.rescan_start)) }
            }
        },
        dismissButton = {
            if (result == null || result is org.elektronnet.mobile.wallet.RescanOutcome.Failed) TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
