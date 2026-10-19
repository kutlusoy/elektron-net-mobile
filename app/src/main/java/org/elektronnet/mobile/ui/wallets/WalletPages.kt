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
) {
    var open by remember { mutableStateOf(false) }
    val active = NodeHost.wallets.firstOrNull { it.name == NodeHost.activeWallet }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { open = true }) {
            Text(walletTitle(active) + "  ▾", style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun item(label: Int, action: () -> Unit) = DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { open = false; action() })
            item(R.string.wallet_menu_details, onDetails)
            item(R.string.wallet_menu_coins, onCoins)
            item(R.string.msg_title, onMessages)
            item(R.string.sweep_title, onSweep)
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
                }
                InfoRow(stringResource(R.string.details_derivation), stringResource(R.string.details_derivation_value, coinType))
            }
        }
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.clickable { copyText(context, "address", row.address) }) {
                        Text(row.address, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
                        row.label?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        Hint(stringResource(R.string.addr_received, Amount.format(row.receivedLep) + " " + stringResource(R.string.balance_unit)) + "  ·  " + stringResource(R.string.addr_tx, row.txCount))
                    }
                }
            }
            Hint(stringResource(R.string.addr_copy_hint))
        }
    }
}

@Composable
fun WalletsScreen(onBack: () -> Unit, onAdd: () -> Unit) {
    BackHandler(onBack = onBack)
    var renaming by remember { mutableStateOf<OwnWallet?>(null) }
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
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wallets_add)) }
        Hint(stringResource(R.string.wallets_hint))
    }
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
