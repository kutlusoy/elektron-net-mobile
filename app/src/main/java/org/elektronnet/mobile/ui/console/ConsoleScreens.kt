package org.elektronnet.mobile.ui.console

import org.elektronnet.mobile.ui.SecretField
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.console.ConsoleSession
import org.elektronnet.mobile.console.Decision
import org.elektronnet.mobile.console.Gate
import org.elektronnet.mobile.console.HistoryEntry
import org.elektronnet.mobile.console.Plan
import org.elektronnet.mobile.console.Refusal
import org.elektronnet.mobile.console.Tier
import org.elektronnet.mobile.service.ConsoleState
import org.elektronnet.mobile.service.ConsoleStore
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.GlassCard
import org.elektronnet.mobile.ui.SecureWindow
import org.elektronnet.mobile.ui.security.Biometric
import org.elektronnet.mobile.ui.theme.ElektronMono
import org.elektronnet.mobile.ui.tools.ErrorText
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.ui.tools.copyText
import org.elektronnet.mobile.ui.tools.pasteText
import org.elektronnet.mobile.wallet.PassphraseCheck

private data class Entry(val command: String, val text: String, val tier: Tier?, val error: Boolean, val truncated: Boolean, val sensitive: Boolean)

@Composable
fun ConsoleRoot(onClose: () -> Unit) {
    SecureWindow(always = true)
    if (ConsoleState.expertActive) ConsoleScreen(onClose) else ConsoleGate(onClose)
}

@Composable
private fun ConsoleGate(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current
    var understood by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            Text(stringResource(R.string.console_title), style = MaterialTheme.typography.titleLarge)
        }
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.console_risk_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.console_risk_body))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { understood = !understood }) {
                    Checkbox(checked = understood, onCheckedChange = null)
                    Text(stringResource(R.string.console_risk_confirm), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (unavailable) ErrorText(stringResource(R.string.console_auth_unavailable))
        Button(
            enabled = understood,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (activity == null || !Biometric.available(context)) {
                    unavailable = true
                } else {
                    Biometric.authenticate(
                        activity,
                        title = context.getString(R.string.console_auth_title),
                        negative = context.getString(R.string.lock_cancel),
                        onSuccess = { ConsoleState.unlock() },
                    )
                }
            },
        ) { Text(stringResource(R.string.console_unlock)) }
    }
}

@Composable
private fun ConsoleScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<Entry>() }
    var input by remember { mutableStateOf("") }
    var wallet by remember { mutableStateOf<String?>(NodeHost.activeWallet) }
    var walletMenu by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var activePlan by remember { mutableStateOf<Plan.Ready?>(null) }
    var runId by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Plan.Ready?>(null) }
    var showDangerSwitch by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showExports by remember { mutableStateOf(false) }
    var needsPassphrase by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val tiers = remember { runCatching { ConsoleStore.tiers(context) }.getOrNull() }
    val retention = NodeHost.store?.consoleRetentionDays ?: 90

    fun leave() {
        entries.clear()
        NodeHost.lockWallet()
        onClose()
    }
    fun endSession() {
        ConsoleState.end()
        leave()
    }
    BackHandler(onBack = ::leave)
    DisposableEffect(Unit) {
        onDispose {
            entries.clear()
            NodeHost.lockWallet()
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { ConsoleStore.housekeeping(context, retention) } }
        while (true) {
            delay(30_000)
            ConsoleState.touchCheck()
            if (!ConsoleState.expertActive) {
                entries.clear()
                NodeHost.lockWallet()
                onClose()
                return@LaunchedEffect
            }
        }
    }
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1) }

    fun add(command: String, text: String, tier: Tier?, error: Boolean, truncated: Boolean = false, sensitive: Boolean = false) {
        entries.add(Entry(command, text, tier, error, truncated, sensitive))
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
    }

    fun execute(plan: Plan.Ready) {
        val runner = NodeHost.consoleRunner() ?: run { add(plan.display, context.getString(R.string.console_node_off), null, true); return }
        running = true
        activePlan = plan
        val id = ++runId
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ConsoleStore.history(context).add(plan.display) }
                runner.execute(plan)
            }
            if (id != runId) return@launch
            running = false
            add(plan.display, result.text, plan.tier, result.error, result.truncated, result.sensitive)
            result.needsPassphraseFor?.let { needsPassphrase = it }
        }
    }

    fun submit() {
        val line = input.trim()
        if (line.isEmpty() || running) return
        ConsoleState.touch()
        if (!ConsoleState.expertActive) {
            leave()
            return
        }
        val runner = NodeHost.consoleRunner()
        if (runner == null) {
            add(line, context.getString(R.string.console_node_off), null, true)
            return
        }
        scope.launch {
            val plan = withContext(Dispatchers.IO) { runner.prepare(line, wallet) }
            when (plan) {
                is Plan.Invalid -> add(line, plan.message, null, true)
                is Plan.Refused -> add(line, context.getString(refusalText(plan.reason)), Tier.Blocked, true)
                is Plan.Ready -> when (Gate.decide(plan.tier, ConsoleState.session)) {
                    Decision.Run -> {
                        input = ""
                        execute(plan)
                    }
                    Decision.NeedsConfirm, Decision.NeedsDangerConfirm -> pending = plan
                    Decision.DangerSwitchOff -> add(plan.display, context.getString(R.string.console_danger_off), plan.tier, true)
                    Decision.Blocked -> add(plan.display, context.getString(R.string.console_blocked), Tier.Blocked, true)
                    Decision.SessionExpired -> leave()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::leave) { Text(stringResource(R.string.action_back)) }
            Text(stringResource(R.string.console_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.console_expert), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = ::endSession) { Text(stringResource(R.string.console_end_session)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = ConsoleState.dangerActive, onCheckedChange = { if (it) showDangerSwitch = true else ConsoleState.setDanger(false) })
            Text(stringResource(R.string.console_danger_switch), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.console_wallet), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { walletMenu = true }) { Text(wallet ?: stringResource(R.string.console_no_wallet)) }
            DropdownMenu(expanded = walletMenu, onDismissRequest = { walletMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.console_no_wallet)) }, onClick = { wallet = null; walletMenu = false })
                consoleWallets().forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { wallet = name; walletMenu = false }) }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (entries.isEmpty()) item { Hint(stringResource(R.string.console_empty)) }
            items(entries) { entry -> EntryView(entry, onCopy = { copyText(context, "result", entry.text) }) }
            if (running) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.console_running), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val plan = activePlan ?: return@TextButton
                        runId++
                        running = false
                        scope.launch {
                            val stopped = withContext(Dispatchers.IO) { NodeHost.consoleRunner()?.abort(plan) == true }
                            add(plan.display, context.getString(if (stopped) R.string.console_stopped else R.string.console_discarded), plan.tier, !stopped)
                        }
                    }) { Text(stringResource(R.string.console_stop)) }
                }
            }
        }
        val first = input.trim().substringBefore(' ')
        val suggestions = if (!input.contains(' ') && first.isNotEmpty() && tiers != null) tiers.names.filter { it.startsWith(first) && it != first }.take(6) else emptyList()
        if (suggestions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollCompat()) {
                suggestions.forEach { name -> OutlinedButton(onClick = { input = "$name " }) { Text(name, fontFamily = ElektronMono, fontSize = 12.sp) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScrollCompat()) {
            listOf("{", "}", "[", "]", "\"", ":", ",").forEach { key -> OutlinedButton(onClick = { input += key }) { Text(key, fontFamily = ElektronMono) } }
            OutlinedButton(onClick = { input += pasteText(context) }) { Text(stringResource(R.string.send_paste)) }
            OutlinedButton(onClick = { showHistory = true }) { Text(stringResource(R.string.console_history)) }
            OutlinedButton(onClick = { showExports = true }) { Text(stringResource(R.string.console_files)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = ElektronMono, fontSize = 14.sp),
                placeholder = { Text(stringResource(R.string.console_hint)) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
            )
            Button(onClick = ::submit, enabled = input.isNotBlank() && !running) { Text(stringResource(R.string.console_run)) }
        }
    }

    pending?.let { plan ->
        ConfirmDialog(
            plan = plan,
            onRun = {
                pending = null
                input = ""
                execute(plan)
            },
            onCancel = { pending = null },
        )
    }
    if (showDangerSwitch) {
        DangerSwitchDialog(onConfirm = { ConsoleState.setDanger(true); showDangerSwitch = false }, onCancel = { showDangerSwitch = false })
    }
    if (showHistory) {
        HistoryDialog(retention, onPick = { input = it; showHistory = false }, onClose = { showHistory = false })
    }
    if (showExports) ExportsDialog(onClose = { showExports = false })
    needsPassphrase?.let { name -> SetPassphraseDialog(name, onDone = { needsPassphrase = null }) }
}

private fun consoleWallets(): List<String> {
    val escrows = NodeHost.store?.escrowStore()?.load().orEmpty().map { it.walletName }
    return (NodeHost.walletNames() + escrows).distinct()
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

@Composable
private fun EntryView(entry: Entry, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("> " + entry.command, fontFamily = ElektronMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Text(
            entry.text,
            fontFamily = ElektronMono,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = if (entry.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (entry.truncated) Hint(stringResource(R.string.console_truncated))
        if (entry.sensitive) Hint(stringResource(R.string.console_sensitive))
        if (!entry.error && entry.text.isNotEmpty() && !entry.sensitive) TextButton(onClick = onCopy) { Text(stringResource(R.string.action_copy)) }
    }
}

@Composable
private fun ConfirmDialog(plan: Plan.Ready, onRun: () -> Unit, onCancel: () -> Unit) {
    val danger = plan.tier == Tier.Danger
    var seconds by remember { mutableStateOf(if (danger) DANGER_DELAY else 0) }
    LaunchedEffect(plan) {
        while (seconds > 0) {
            delay(1_000)
            seconds--
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(if (danger) R.string.console_confirm_danger_title else R.string.console_confirm_title), color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.display, fontFamily = ElektronMono, fontSize = 13.sp)
                plan.wallet?.let { Hint(stringResource(R.string.console_confirm_wallet, it)) }
                if (danger) Text(stringResource(R.string.console_confirm_danger_body), color = MaterialTheme.colorScheme.error)
                if (plan.heavy) Hint(stringResource(R.string.console_heavy_note))
            }
        },
        confirmButton = {
            TextButton(onClick = onRun, enabled = seconds == 0) {
                Text(if (seconds > 0) stringResource(R.string.console_run_in, seconds) else stringResource(R.string.console_run))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DangerSwitchDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.console_danger_title), color = MaterialTheme.colorScheme.error) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.console_danger_body))
                Text(stringResource(R.string.console_danger_type, ConsoleSession.DANGER_TOKEN), fontFamily = ElektronMono)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.Characters),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = typed.trim() == ConsoleSession.DANGER_TOKEN) { Text(stringResource(R.string.console_danger_enable)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun HistoryDialog(retention: Int, onPick: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var days by remember { mutableStateOf(retention) }
    LaunchedEffect(Unit) { items = withContext(Dispatchers.IO) { ConsoleStore.history(context).all() } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.console_history)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Hint(stringResource(R.string.console_history_hint))
                listOf(0, 7, 30, 90).forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        days = option
                        NodeHost.store?.consoleRetentionDays = option
                        scope.launch { withContext(Dispatchers.IO) { ConsoleStore.history(context).prune(option) }; items = withContext(Dispatchers.IO) { ConsoleStore.history(context).all() } }
                    }) {
                        RadioButton(selected = days == option, onClick = null)
                        Text(if (option == 0) stringResource(R.string.console_retention_off) else stringResource(R.string.console_retention_days, option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (items.isEmpty()) Text(stringResource(R.string.console_history_empty))
                items.take(100).forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.text, fontFamily = ElektronMono, fontSize = 12.sp, modifier = Modifier.weight(1f).clickable { onPick(entry.text) })
                        TextButton(onClick = {
                            scope.launch { withContext(Dispatchers.IO) { ConsoleStore.history(context).remove(entry.id) }; items = items.filter { it.id != entry.id } }
                        }) { Text(stringResource(R.string.explorer_remove)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { ConsoleStore.history(context).clear() }; items = emptyList() } }) {
                Text(stringResource(R.string.console_history_clear), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun ExportsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var saving by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val file = saving
        saving = null
        if (uri != null && file != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } }
            }
        }
    }
    fun reload() {
        files = listExports(context)
    }
    LaunchedEffect(Unit) { reload() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.console_files)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint(stringResource(R.string.console_files_hint))
                if (files.isEmpty()) Text(stringResource(R.string.console_files_empty))
                val root = ConsoleStore.exportsDir(context)
                files.forEach { file ->
                    Column {
                        Text(file.relativeTo(root).path, fontFamily = ElektronMono, fontSize = 12.sp)
                        Hint("${file.length() / 1024} KB")
                        Row {
                            TextButton(onClick = { saving = file; launcher.launch(file.name) }) { Text(stringResource(R.string.console_save_to)) }
                            TextButton(onClick = { file.delete(); reload() }) { Text(stringResource(R.string.explorer_remove), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_ok)) } },
    )
}

private fun listExports(context: Context): List<File> =
    ConsoleStore.exportsDir(context).walkTopDown().filter { it.isFile }.sortedByDescending { it.lastModified() }.toList()

@Composable
private fun SetPassphraseDialog(wallet: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val ok = PassphraseCheck.acceptable(pass) && pass == repeat
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.console_restore_pass_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.console_restore_pass_body, wallet))
                SecretField(value = pass, onValueChange = { pass = it }, singleLine = true, label = { Text(stringResource(R.string.fr_pass_label)) }, )
                SecretField(value = repeat, onValueChange = { repeat = it }, singleLine = true, label = { Text(stringResource(R.string.fr_pass_confirm)) }, )
                if (failed) ErrorText(stringResource(R.string.console_restore_pass_failed))
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                val secret = pass
                pass = ""
                repeat = ""
                scope.launch {
                    val success = withContext(Dispatchers.IO) { runCatching { NodeHost.consoleRpc()?.call("encryptwallet", listOf(secret), wallet) }.isSuccess }
                    if (success) onDone() else failed = true
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { NodeHost.consoleRpc()?.call("unloadwallet", listOf(wallet), null) } }
                    onDone()
                }
            }) { Text(stringResource(R.string.console_restore_pass_unload)) }
        },
    )
}

private fun refusalText(reason: Refusal): Int = when (reason) {
    Refusal.Blocked -> R.string.console_blocked
    Refusal.NoPassphrase -> R.string.console_ref_no_passphrase
    Refusal.WatchOnly -> R.string.console_ref_watch_only
    Refusal.WalletNotEncrypted -> R.string.console_ref_not_encrypted
    Refusal.NeedsWallet -> R.string.console_ref_needs_wallet
    Refusal.RollbackNotAllowed -> R.string.console_ref_rollback
    Refusal.DumpTypeMissing -> R.string.console_ref_dump_type
    Refusal.LowStorage -> R.string.console_ref_storage
    Refusal.PathEmpty, Refusal.PathBad -> R.string.console_ref_path_bad
    Refusal.PathTraversal, Refusal.PathOutside, Refusal.PathSymlink -> R.string.console_ref_path_outside
    Refusal.PathExists -> R.string.console_ref_path_exists
    Refusal.PathMissing -> R.string.console_ref_path_missing
    Refusal.NodeNotReady -> R.string.console_node_off
}

private const val MAX_ENTRIES = 300
private const val DANGER_DELAY = 5
