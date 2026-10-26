package org.elektronnet.mobile.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.send.SendScreen
import org.elektronnet.mobile.wallet.EscrowRecord

enum class ToolsPage { Hub, Escrows, EscrowDetail, Psbt, Sweep, Messages }

private typealias Page = ToolsPage

@Composable
fun ToolsScreen(onClose: () -> Unit, start: ToolsPage = ToolsPage.Hub, scanSigned: Boolean = false) {
    var page by remember { mutableStateOf(start) }
    val backToHub = { if (start != ToolsPage.Hub && page == start) onClose() else page = Page.Hub }
    var escrowId by remember { mutableStateOf(0) }
    var paying by remember { mutableStateOf<EscrowRecord?>(null) }
    var labels by remember { mutableStateOf(mapOf(NodeHost.activeWallet to "")) }
    val mainLabel = stringResource(R.string.tools_main_wallet)

    LaunchedEffect(page) {
        val records = withContext(Dispatchers.IO) { NodeHost.escrowManager()?.list().orEmpty() }
        labels = NodeHost.wallets.associate { it.name to it.label.ifBlank { mainLabel } } + (NodeHost.activeWallet to NodeHost.wallets.firstOrNull { it.name == NodeHost.activeWallet }?.label.orEmpty().ifBlank { mainLabel }) + records.associate { it.walletName to it.name }
    }
    BackHandler {
        when {
            paying != null -> paying = null
            page == Page.Hub -> onClose()
            page == Page.EscrowDetail -> page = Page.Escrows
            else -> backToHub()
        }
    }

    val pay = paying
    when {
        pay != null -> SendScreen(initialUri = null, onClose = { paying = null }, onDone = { paying = null }, escrow = pay)
        page == Page.Hub -> ToolPage(stringResource(R.string.tools_title), onClose) {
            Hint(stringResource(R.string.tools_intro))
            val watchOnly = NodeHost.isWatchOnly()
            if (!watchOnly) ToolEntry(R.string.escrow_title, R.string.tools_escrow_desc) { page = Page.Escrows }
            ToolEntry(R.string.psbt_title, R.string.tools_psbt_desc) { page = Page.Psbt }
            ToolEntry(R.string.sweep_title, R.string.tools_sweep_desc) { page = Page.Sweep }
            if (!watchOnly) ToolEntry(R.string.msg_title, R.string.tools_msg_desc) { page = Page.Messages }
            if (watchOnly) Hint(stringResource(R.string.watch_tools_hint))
        }
        page == Page.Escrows -> EscrowListScreen(onBack = { page = Page.Hub }, onOpen = { escrowId = it; page = Page.EscrowDetail })
        page == Page.EscrowDetail -> EscrowDetailScreen(
            id = escrowId,
            onBack = { page = Page.Escrows },
            onPay = { paying = it },
            onPsbt = { page = Page.Psbt },
            onDeposit = { NodeHost.incomingUri = it },
        )
        page == Page.Psbt -> PsbtScreen(labels, onBack = backToHub, scanFirst = scanSigned && start == ToolsPage.Psbt)
        page == Page.Sweep -> SweepScreen(onBack = backToHub)
        page == Page.Messages -> MessageScreen(onBack = backToHub)
    }
}

@Composable
private fun ToolEntry(title: Int, description: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(title))
            Hint(stringResource(description))
        }
    }
}
