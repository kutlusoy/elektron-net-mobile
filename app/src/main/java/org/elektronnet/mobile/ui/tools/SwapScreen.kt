package org.elektronnet.mobile.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.kdf.CoinBalance
import org.elektronnet.mobile.kdf.KdfState
import org.elektronnet.mobile.kdf.MakerOrder
import org.elektronnet.mobile.kdf.Orderbook
import org.elektronnet.mobile.kdf.SwapStatus
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.style.TextAlign
import org.elektronnet.mobile.ui.theme.ElektronMono
import org.elektronnet.mobile.ui.tools.copyText
import org.elektronnet.mobile.ui.receive.QrView
import org.elektronnet.mobile.service.NodeHost

private val TESTNET_PAIRS = listOf(
    "tELEK" to "tBTC",
    "tELEK" to "SEPOLIAETH",
    "tBTC" to "SEPOLIAETH",
)

private val MAINNET_PAIRS = listOf(
    "ELEK" to "BTC",
    "ELEK" to "ETH",
    "BTC" to "ETH",
)

@Composable
fun SwapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kdf = NodeHost.kdfSupervisor

    val availablePairs = remember {
        if (ChainConfig.isTestnet) TESTNET_PAIRS else MAINNET_PAIRS
    }
    var selectedPairIndex by remember { mutableIntStateOf(0) }
    val currentPair = availablePairs[selectedPairIndex]
    val base = currentPair.first
    val rel = currentPair.second

    var orderbook by remember { mutableStateOf<Orderbook?>(null) }
    var baseBalance by remember { mutableStateOf<CoinBalance?>(null) }
    var relBalance by remember { mutableStateOf<CoinBalance?>(null) }
    var myOrders by remember { mutableStateOf<List<MakerOrder>>(emptyList()) }
    var recentSwaps by remember { mutableStateOf<List<SwapStatus>>(emptyList()) }

    var actionTab by remember { mutableIntStateOf(0) } // 0: Instant Swap (Taker), 1: Limit Order (Maker)
    var swapVolume by remember { mutableStateOf("") }
    var makerPrice by remember { mutableStateOf("") }
    var makerVolume by remember { mutableStateOf("") }

    var message by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    var withdrawCoin by remember { mutableStateOf<String?>(null) }
    var withdrawInitialAmount by remember { mutableStateOf("") }
    var showShareAddressDialog by remember { mutableStateOf(false) }

    fun refreshData() {
        val client = kdf?.client ?: return
        if (kdf.state != KdfState.Running) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val ob = client.orderbook(base, rel)
                val bb = runCatching { client.myBalance(base) }.getOrNull()
                val rb = runCatching { client.myBalance(rel) }.getOrNull()
                val ords = client.myOrders().makerOrders
                val swaps = kdf.getRecentSwapsMerged(10)
                withContext(Dispatchers.Main) {
                    orderbook = ob
                    baseBalance = bb
                    relBalance = rb
                    myOrders = ords
                    recentSwaps = swaps
                }
            }
        }
    }

    LaunchedEffect(kdf?.state, selectedPairIndex) {
        if (kdf?.state == KdfState.Running) {
            while (true) {
                refreshData()
                delay(3_000)
            }
        }
    }

    ToolPage(title = stringResource(R.string.swap_title), onBack = onBack) {
        // Status & Lifecycle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = when (kdf?.state) {
                            is KdfState.Running -> Color(0xFF4CAF50)
                            is KdfState.Starting -> Color(0xFFFF9800)
                            is KdfState.Error -> MaterialTheme.colorScheme.error
                            else -> Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (kdf?.state) {
                                is KdfState.Running -> "KDF P2P Mesh Aktiv"
                                is KdfState.Starting -> "KDF Startet..."
                                is KdfState.Error -> "KDF Fehler"
                                else -> "KDF Gestoppt"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    when (kdf?.state) {
                        is KdfState.Stopped, is KdfState.Error -> {
                            Button(
                                onClick = { kdf.start() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Text("Starten")
                            }
                        }
                        is KdfState.Running -> {
                            OutlinedButton(onClick = { kdf.stop() }) {
                                Text("Stoppen")
                            }
                        }
                        is KdfState.Starting -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        null -> {}
                    }
                }

                if (kdf?.state is KdfState.Error) {
                    ErrorText((kdf.state as KdfState.Error).message)
                }
                if (kdf?.state == KdfState.Running && kdf.versionInfo.isNotBlank()) {
                    Text(
                        kdf.versionInfo,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (kdf?.state != KdfState.Running) {
            Hint("Starte den KDF Swap Daemon, um dezentral über das Peer-to-Peer Mesh zu handeln.")
            return@ToolPage
        }

        // Trading Pair Selector
        Text("Handelspaar", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availablePairs.forEachIndexed { index, (b, r) ->
                val selected = index == selectedPairIndex
                OutlinedButton(
                    onClick = {
                        selectedPairIndex = index
                        orderbook = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (selected) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) else ButtonDefaults.outlinedButtonColors(),
                ) {
                    Text("$b / $r", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Share Address Button
        OutlinedButton(
            onClick = { showShareAddressDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("⚡ " + stringResource(R.string.swap_share_address))
        }

        // Balances Row
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(base + " (" + stringResource(R.string.swap_kdf_balance) + ")", style = MaterialTheme.typography.labelSmall)
                    Text(
                        baseBalance?.balance?.toPlainString() ?: "0.00",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if ((baseBalance?.balance ?: java.math.BigDecimal.ZERO) > java.math.BigDecimal.ZERO) {
                        TextButton(onClick = {
                            withdrawCoin = base
                            withdrawInitialAmount = baseBalance?.balance?.toPlainString().orEmpty()
                        }) {
                            Text(stringResource(R.string.swap_withdraw_action), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rel + " (" + stringResource(R.string.swap_kdf_balance) + ")", style = MaterialTheme.typography.labelSmall)
                    Text(
                        relBalance?.balance?.toPlainString() ?: "0.00",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if ((relBalance?.balance ?: java.math.BigDecimal.ZERO) > java.math.BigDecimal.ZERO) {
                        TextButton(onClick = {
                            withdrawCoin = rel
                            withdrawInitialAmount = relBalance?.balance?.toPlainString().orEmpty()
                        }) {
                            Text(stringResource(R.string.swap_withdraw_action), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Action Tabs: Sofort-Tausch vs Limit Order
        TabRow(selectedTabIndex = actionTab) {
            Tab(selected = actionTab == 0, onClick = { actionTab = 0 }, text = { Text("Sofort-Tausch") })
            Tab(selected = actionTab == 1, onClick = { actionTab = 1 }, text = { Text("Limit-Order") })
        }

        // Form Fields
        if (actionTab == 0) {
            // Instant Swap (Taker)
            val bestAsk = orderbook?.asks?.minByOrNull { it.price }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sofort kaufen ($base gegen $rel)", style = MaterialTheme.typography.titleSmall)
                    if (bestAsk != null) {
                        Text(
                            "Bestes Angebot: ${bestAsk.price.toPlainString()} $rel pro $base (Verfügbar: ${bestAsk.maxVolume.toPlainString()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Hint("Aktuell kein passendes Verkaufsangebot im Orderbuch.")
                    }

                    OutlinedTextField(
                        value = swapVolume,
                        onValueChange = { swapVolume = it },
                        label = { Text("Menge ($base)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    val estimatedCost = runCatching {
                        val vol = BigDecimal(swapVolume.ifBlank { "0" })
                        val pr = bestAsk?.price ?: BigDecimal.ZERO
                        (vol * pr).toPlainString()
                    }.getOrDefault("0")

                    if (swapVolume.isNotBlank() && bestAsk != null) {
                        Text("Geschätzte Kosten: $estimatedCost $rel", style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            val client = kdf.client
                            val pr = bestAsk?.price?.toPlainString() ?: return@Button
                            val vol = swapVolume.trim()
                            if (vol.isBlank()) return@Button
                            isOperating = true
                            errorMessage = null
                            message = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val uuid = client.buy(base, rel, pr, vol)
                                    withContext(Dispatchers.Main) {
                                        message = "Swap gestartet! UUID: ${uuid?.take(8)}..."
                                        swapVolume = ""
                                        isOperating = false
                                        refreshData()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = e.message
                                        isOperating = false
                                    }
                                }
                            }
                        },
                        enabled = !isOperating && swapVolume.isNotBlank() && bestAsk != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isOperating) "Tausche..." else "Jetzt tauschen ($base kaufen)")
                    }
                }
            }
        } else {
            // Limit Order (Maker)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Limit-Order einstellen ($base verkaufen gegen $rel)", style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = makerPrice,
                        onValueChange = { makerPrice = it },
                        label = { Text("Preis ($rel pro $base)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    OutlinedTextField(
                        value = makerVolume,
                        onValueChange = { makerVolume = it },
                        label = { Text("Menge ($base)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    Button(
                        onClick = {
                            val client = kdf.client
                            val pr = makerPrice.trim()
                            val vol = makerVolume.trim()
                            if (pr.isBlank() || vol.isBlank()) return@Button
                            isOperating = true
                            errorMessage = null
                            message = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    client.setprice(base, rel, pr, vol, cancelPrevious = false)
                                    withContext(Dispatchers.Main) {
                                        message = "Order erfolgreich ins Orderbuch gestellt!"
                                        makerPrice = ""
                                        makerVolume = ""
                                        isOperating = false
                                        refreshData()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = e.message
                                        isOperating = false
                                    }
                                }
                            }
                        },
                        enabled = !isOperating && makerPrice.isNotBlank() && makerVolume.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isOperating) "Stelle Order..." else "Order ins Orderbuch stellen")
                    }
                }
            }
        }

        if (message != null) {
            Text(message.orEmpty(), color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
        }
        if (errorMessage != null) {
            ErrorText(errorMessage.orEmpty())
        }

        // Live Orderbook
        Text("Orderbuch ($base / $rel)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Preis ($rel)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Menge ($base)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()

                // Asks (Sells)
                val asks = orderbook?.asks.orEmpty().take(5)
                if (asks.isEmpty()) {
                    Text("Keine Verkaufsangebote", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    asks.forEach { ask ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    actionTab = 0
                                    swapVolume = ask.maxVolume.toPlainString()
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(ask.price.toPlainString(), color = Color(0xFFE53935), fontFamily = FontFamily.Monospace)
                            Text(ask.maxVolume.toPlainString(), fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Bids (Buys)
                val bids = orderbook?.bids.orEmpty().take(5)
                if (bids.isEmpty()) {
                    Text("Keine Kaufangebote", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    bids.forEach { bid ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(bid.price.toPlainString(), color = Color(0xFF43A047), fontFamily = FontFamily.Monospace)
                            Text(bid.maxVolume.toPlainString(), fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Active Maker Orders
        if (myOrders.isNotEmpty()) {
            Text("Meine aktiven Maker-Orders (${myOrders.size})", style = MaterialTheme.typography.titleMedium)
            myOrders.forEach { ord ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${ord.base} -> ${ord.rel}", fontWeight = FontWeight.SemiBold)
                            Text("Preis: ${ord.price.toPlainString()} | Offen: ${ord.availableAmount.toPlainString()}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (ord.cancellable) {
                            TextButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        kdf.client.cancelOrder(ord.uuid)
                                        refreshData()
                                    }
                                },
                            ) {
                                Text("Stornieren", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Recent Swaps
        if (recentSwaps.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Letzte Swaps", style = MaterialTheme.typography.titleMedium)
                val unprunedCount = recentSwaps.count { it.state == "Finished" && !it.isPruned }
                if (unprunedCount > 0) {
                    TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                kdf.pruner.pruneAllFinished(recentSwaps)
                                refreshData()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.swap_prune_all), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            recentSwaps.forEach { sw ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (sw.isPruned) "${sw.myAmount.toPlainString()} ${sw.myCoin} ⇄ ${sw.otherAmount.toPlainString()} ${sw.otherCoin}"
                                else "${sw.myAmount.toPlainString()} ${sw.myCoin} -> ${sw.otherAmount.toPlainString()} ${sw.otherCoin}",
                                fontWeight = FontWeight.SemiBold,
                            )
                            val stateColor = when (sw.state) {
                                "Finished" -> Color(0xFF2E7D32)
                                "Failed" -> MaterialTheme.colorScheme.error
                                else -> Color(0xFFE65100)
                            }
                            Text(sw.state, color = stateColor, fontWeight = FontWeight.Bold)
                        }

                        if (sw.isPruned) {
                            val priceStr = if (sw.myAmount > java.math.BigDecimal.ZERO) {
                                try {
                                    sw.otherAmount.divide(sw.myAmount, 6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                                } catch (_: Exception) {
                                    "-"
                                }
                            } else "-"
                            Text(
                                stringResource(R.string.swap_pruned_rate, "$priceStr ${sw.otherCoin}/${sw.myCoin}"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                stringResource(R.string.swap_pruned_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (!sw.secretHash.isNullOrEmpty()) {
                                Text(
                                    stringResource(R.string.swap_pruned_proof, sw.secretHash.take(16) + "..."),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text("UUID: ${sw.uuid.take(12)}...", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            if (sw.events.isNotEmpty()) {
                                Text(
                                    "Schritte: " + sw.events.joinToString(" -> ") { it.type },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (sw.state == "Finished") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (sw.otherAmount > java.math.BigDecimal.ZERO) {
                                        Button(
                                            onClick = {
                                                withdrawCoin = sw.otherCoin
                                                withdrawInitialAmount = sw.otherAmount.toPlainString()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(stringResource(R.string.swap_claim_reward, sw.otherAmount.toPlainString(), sw.otherCoin))
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                kdf.pruner.pruneSwap(sw)
                                                refreshData()
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.swap_prune_single), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (withdrawCoin != null) {
            val avail = if (withdrawCoin == base) baseBalance?.balance ?: java.math.BigDecimal.ZERO else relBalance?.balance ?: java.math.BigDecimal.ZERO
            WithdrawDialog(
                coin = withdrawCoin!!,
                initialAmount = withdrawInitialAmount,
                availableBalance = avail,
                onDismiss = {
                    withdrawCoin = null
                    withdrawInitialAmount = ""
                },
                onSuccess = { txId ->
                    message = "Auszahlung erfolgreich! TxID: ${txId.take(16)}..."
                    val finishedToPrune = recentSwaps.find { it.state == "Finished" && !it.isPruned && it.otherCoin == withdrawCoin }
                    if (finishedToPrune != null) {
                        scope.launch(Dispatchers.IO) {
                            kdf.pruner.pruneSwap(finishedToPrune)
                            refreshData()
                        }
                    } else {
                        refreshData()
                    }
                    withdrawCoin = null
                    withdrawInitialAmount = ""
                },
            )
        }

        if (showShareAddressDialog) {
            ShareAddressDialog(
                initialCoin = rel,
                onDismiss = { showShareAddressDialog = false },
            )
        }
    }
}

@Composable
private fun WithdrawDialog(
    coin: String,
    initialAmount: String = "",
    availableBalance: java.math.BigDecimal,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val wallets = NodeHost.wallets
    val preferredWallet = remember(wallets, coin) {
        if (coin.contains("BTC", ignoreCase = true)) {
            wallets.firstOrNull { it.name.contains("btc", ignoreCase = true) || it.label.contains("btc", ignoreCase = true) }?.name ?: NodeHost.activeWallet
        } else {
            NodeHost.activeWallet
        }
    }
    var selectedWallet by remember { mutableStateOf(preferredWallet) }
    var amount by remember { mutableStateOf(initialAmount) }
    var inProgress by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var targetAddress by remember { mutableStateOf("") }

    LaunchedEffect(selectedWallet) {
        targetAddress = withContext(Dispatchers.IO) {
            runCatching { NodeHost.receiveWallet(selectedWallet)?.currentAddress() }.getOrNull().orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text(stringResource(R.string.swap_withdraw_title) + " ($coin)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.swap_withdraw_target_wallet), style = MaterialTheme.typography.labelMedium)
                wallets.forEach { w ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !inProgress) {
                                selectedWallet = w.name
                            },
                    ) {
                        RadioButton(
                            selected = w.name == selectedWallet,
                            onClick = {
                                selectedWallet = w.name
                            },
                            enabled = !inProgress,
                        )
                        Text(w.label.ifBlank { w.name } + if (w.name == NodeHost.activeWallet) " (Aktiv)" else "")
                    }
                }

                if (targetAddress.isNotBlank()) {
                    Text("Empfangsadresse: $targetAddress", fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.swap_withdraw_amount) + " ($coin)") },
                    singleLine = true,
                    enabled = !inProgress,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { amount = availableBalance.toPlainString() }, enabled = !inProgress) {
                            Text(stringResource(R.string.swap_withdraw_max))
                        }
                    },
                )
                Text("Verfügbar im Mesh: ${availableBalance.toPlainString()} $coin", style = MaterialTheme.typography.bodySmall)

                if (errorText != null) {
                    ErrorText(errorText!!)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    inProgress = true
                    errorText = null
                    scope.launch {
                        val res = NodeHost.withdrawFromKdfToWallet(
                            coin = coin,
                            targetWallet = selectedWallet,
                            amount = amount.trim().takeIf { it.isNotBlank() },
                            max = amount.isBlank() || amount == availableBalance.toPlainString(),
                        )
                        inProgress = false
                        res.fold(
                            onSuccess = { txHash -> onSuccess(txHash) },
                            onFailure = { err -> errorText = err.message ?: err.toString() },
                        )
                    }
                },
                enabled = !inProgress && (amount.isNotBlank() || availableBalance > java.math.BigDecimal.ZERO),
            ) {
                Text(if (inProgress) stringResource(R.string.swap_withdraw_in_progress) else stringResource(R.string.swap_withdraw_execute))
            }
        },
        dismissButton = {
            if (!inProgress) {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun ShareAddressDialog(
    initialCoin: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val wallets = NodeHost.wallets
    var selectedCoin by remember { mutableStateOf(initialCoin) }
    val preferredWallet = remember(wallets, selectedCoin) {
        if (selectedCoin.contains("BTC", ignoreCase = true)) {
            wallets.firstOrNull { it.name.contains("btc", ignoreCase = true) || it.label.contains("btc", ignoreCase = true) }?.name ?: NodeHost.activeWallet
        } else {
            NodeHost.activeWallet
        }
    }
    var selectedWallet by remember { mutableStateOf(preferredWallet) }
    var currentAddress by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(selectedWallet, selectedCoin) {
        currentAddress = withContext(Dispatchers.IO) {
            runCatching { NodeHost.receiveWallet(selectedWallet)?.currentAddress() }.getOrNull().orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.swap_share_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("tBTC", "tELEK").forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedCoin = c }) {
                            RadioButton(selected = selectedCoin == c, onClick = { selectedCoin = c })
                            Text(c)
                        }
                    }
                }

                Text(stringResource(R.string.swap_withdraw_target_wallet), style = MaterialTheme.typography.labelMedium)
                wallets.forEach { w ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedWallet = w.name
                            },
                    ) {
                        RadioButton(
                            selected = w.name == selectedWallet,
                            onClick = {
                                selectedWallet = w.name
                            },
                        )
                        Text(w.label.ifBlank { w.name })
                    }
                }

                if (currentAddress.isNotBlank()) {
                    Box(modifier = Modifier.size(200.dp)) {
                        QrView(currentAddress)
                    }
                    Text(
                        currentAddress,
                        fontFamily = ElektronMono,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable {
                            copyText(context, "address", currentAddress)
                            copied = true
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            copyText(context, "address", currentAddress)
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (copied) stringResource(R.string.swap_address_copied) else stringResource(R.string.swap_copy_address))
                    }
                }

                Hint(stringResource(R.string.swap_share_hint))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}
