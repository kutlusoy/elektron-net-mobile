package org.elektronnet.mobile.ui.firstrun

import org.elektronnet.mobile.ui.SecretField
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.runtime.Headline
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.BrandBackground
import org.elektronnet.mobile.ui.BrandHeader
import org.elektronnet.mobile.ui.GlassCard
import org.elektronnet.mobile.ui.HeroCard
import org.elektronnet.mobile.ui.SecureWindow
import org.elektronnet.mobile.wallet.BackupQuiz
import org.elektronnet.mobile.wallet.Bip39
import org.elektronnet.mobile.wallet.DiceCheck
import org.elektronnet.mobile.wallet.DiceIssue
import org.elektronnet.mobile.wallet.EntropyGenerator
import org.elektronnet.mobile.wallet.PhraseStrength
import org.elektronnet.mobile.wallet.SetupRequest
import org.elektronnet.mobile.wallet.TouchEntropy
import org.elektronnet.mobile.wallet.BootstrapFailure
import org.elektronnet.mobile.wallet.BootstrapResult
import org.elektronnet.mobile.wallet.FirstRunFlow
import org.elektronnet.mobile.wallet.FirstRunState
import org.elektronnet.mobile.wallet.FirstRunStep
import org.elektronnet.mobile.wallet.PassphraseCheck
import org.elektronnet.mobile.wallet.PassphraseStrength
import org.elektronnet.mobile.ui.theme.ElektronMono

@Composable
fun FirstRunScreen(onAllowMetered: (Boolean) -> Unit, onFinished: () -> Unit, addWallet: Boolean = false, onCancel: () -> Unit = {}, onSkip: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(FirstRunState(addWallet = addWallet)) }
    var walletLabel by remember { mutableStateOf("") }
    val walletName = remember { if (addWallet) NodeHost.nextWalletName() else org.elektronnet.mobile.wallet.WalletBootstrap.DEFAULT_WALLET }
    var passphrase by remember { mutableStateOf("") }
    var strength by remember { mutableStateOf(PhraseStrength.Words12) }
    var entropyError by remember { mutableStateOf(false) }
    val entropyGenerator = remember { EntropyGenerator() }

    val bip39 = remember {
        Bip39(context.assets.open("bip39/english.txt").bufferedReader().readLines().filter { it.isNotBlank() })
    }
    val secureStep = state.step in setOf(
        FirstRunStep.PhraseDice, FirstRunStep.PhraseTouch, FirstRunStep.PhraseShow, FirstRunStep.PhraseQuiz, FirstRunStep.PassphraseNew,
        FirstRunStep.RestoreEnter, FirstRunStep.PassphraseRestore, FirstRunStep.Restoring,
    )
    SecureWindow(secureStep, always = true)

    BackHandler(enabled = FirstRunFlow.back(state) != null || addWallet) { FirstRunFlow.back(state)?.let { state = it } ?: onCancel() }

    fun submit(recover: Boolean) {
        NodeHost.beginSetup(SetupRequest(state.phrase, passphrase, recover, walletName), walletLabel)
        passphrase = ""
        state = state.copy(step = FirstRunStep.Done, phrase = emptyList())
        onFinished()
    }

    BrandBackground {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BrandHeader(compact = state.step != FirstRunStep.Welcome)
        GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state.step) {
            FirstRunStep.Welcome -> {
                Text(stringResource(if (addWallet) R.string.add_wallet_title else R.string.fr_welcome_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(if (addWallet) R.string.add_wallet_body else R.string.fr_welcome_body))
                if (addWallet) {
                    androidx.compose.material3.OutlinedTextField(
                        value = walletLabel,
                        onValueChange = { walletLabel = it.take(32) },
                        label = { Text(stringResource(R.string.wallets_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = { state = FirstRunFlow.next(state.copy(restore = false)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_create)) }
                OutlinedButton(
                    onClick = { state = FirstRunFlow.next(state.copy(restore = true)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_restore)) }
                if (addWallet) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                } else {
                    if (onSkip != null) {
                        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fr_skip)) }
                        Text(stringResource(R.string.fr_skip_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { state = state.copy(step = FirstRunStep.HowItWorks) }) {
                        Text(stringResource(R.string.fr_how_link))
                    }
                }
            }
            FirstRunStep.HowItWorks -> {
                Text(stringResource(R.string.fr_how_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.fr_how_body))
                Button(onClick = { state = FirstRunFlow.next(state) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_ok))
                }
            }
            FirstRunStep.Network -> {
                Text(stringResource(R.string.fr_network_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.fr_network_body))
                Button(
                    onClick = {
                        onAllowMetered(false)
                        state = FirstRunFlow.next(state)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_network_wifi)) }
                OutlinedButton(
                    onClick = {
                        onAllowMetered(true)
                        state = FirstRunFlow.next(state)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_network_any)) }
            }
            FirstRunStep.Notifications -> {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    state = FirstRunFlow.next(state)
                }
                Text(stringResource(R.string.fr_notify_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.fr_notify_body))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            state = FirstRunFlow.next(state)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_continue)) }
            }
            FirstRunStep.PhraseIntro -> {
                Text(stringResource(R.string.fr_phrase_intro_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.fr_phrase_warning))
                Text(stringResource(R.string.fr_strength_label))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = strength == PhraseStrength.Words12,
                        onClick = { strength = PhraseStrength.Words12 },
                        label = { Text(stringResource(R.string.fr_strength_12)) },
                    )
                    FilterChip(
                        selected = strength == PhraseStrength.Words24,
                        onClick = { strength = PhraseStrength.Words24 },
                        label = { Text(stringResource(R.string.fr_strength_24)) },
                    )
                }
                if (entropyError) Text(stringResource(R.string.fr_entropy_error), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        entropyError = false
                        val entropy = runCatching { entropyGenerator.fromSystem(strength) }.getOrNull()
                        if (entropy == null) {
                            entropyError = true
                        } else {
                            val words = bip39.fromEntropy(entropy)
                            entropy.fill(0)
                            state = FirstRunFlow.next(state.copy(phrase = words))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_gen_phone)) }
                OutlinedButton(
                    onClick = { state = state.copy(step = FirstRunStep.PhraseTouch) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_gen_touch)) }
                OutlinedButton(
                    onClick = { state = state.copy(step = FirstRunStep.PhraseDice) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_gen_dice)) }
            }
            FirstRunStep.PhraseTouch -> {
                val touch = remember { TouchEntropy() }
                var progress by remember { mutableStateOf(0f) }
                var trail by remember { mutableStateOf(listOf<Offset>()) }
                val lineColor = MaterialTheme.colorScheme.primary
                Text(stringResource(R.string.fr_touch_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.fr_touch_body))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { change ->
                                        if (change.pressed) {
                                            touch.add(change.position.x, change.position.y, System.nanoTime(), change.pressure)
                                            progress = touch.progress
                                            trail = (trail + change.position).takeLast(160)
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        trail.zipWithNext { a, b -> drawLine(lineColor, a, b, strokeWidth = 4f) }
                    }
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                if (entropyError) Text(stringResource(R.string.fr_entropy_error), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        entropyError = false
                        val extra = touch.finish()
                        val entropy = runCatching { entropyGenerator.fromSystem(strength, extra) }.getOrNull()
                        extra.fill(0)
                        if (entropy == null) {
                            entropyError = true
                        } else {
                            val words = bip39.fromEntropy(entropy)
                            entropy.fill(0)
                            state = FirstRunFlow.next(state.copy(phrase = words))
                        }
                    },
                    enabled = progress >= 1f,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_dice_create)) }
            }
            FirstRunStep.PhraseDice -> {
                var rolls by remember { mutableStateOf("") }
                var diceOnly by remember { mutableStateOf(false) }
                val check = DiceCheck.evaluate(rolls, strength)
                val needed = strength.minRolls
                Text(stringResource(R.string.fr_dice_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.fr_dice_body, needed))
                OutlinedTextField(
                    value = rolls,
                    onValueChange = { input -> rolls = input.filter { it in '1'..'6' }.take(DiceCheck.MAX_ROLLS) },
                    label = { Text(stringResource(R.string.fr_dice_label)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.fr_dice_count, check.rolls, needed, check.bits.toInt()))
                val issueText = when (check.warning) {
                    null, DiceIssue.TooFew, DiceIssue.InvalidCharacter, DiceIssue.TooMany -> null
                    DiceIssue.LongRun -> R.string.fr_dice_issue_run
                    DiceIssue.RepeatedPattern -> R.string.fr_dice_issue_pattern
                    DiceIssue.Biased -> R.string.fr_dice_issue_bias
                }
                issueText?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                var confirmed by remember { mutableStateOf(false) }
                val needsConfirm = diceOnly && check.warning != null
                if (needsConfirm) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text(stringResource(R.string.fr_dice_confirm), modifier = Modifier.padding(start = 12.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = diceOnly, onCheckedChange = { diceOnly = it })
                    Text(stringResource(R.string.fr_dice_only), modifier = Modifier.padding(start = 12.dp))
                }
                Text(stringResource(if (diceOnly) R.string.fr_dice_only_hint else R.string.fr_dice_mix_hint))
                if (entropyError) Text(stringResource(R.string.fr_entropy_error), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        entropyError = false
                        val entropy = runCatching { entropyGenerator.fromDice(strength, rolls, mixSystem = !diceOnly) }.getOrNull()
                        if (entropy == null) {
                            entropyError = true
                        } else {
                            val words = bip39.fromEntropy(entropy)
                            entropy.fill(0)
                            state = FirstRunFlow.next(state.copy(phrase = words))
                        }
                    },
                    enabled = check.issue == null && (!needsConfirm || confirmed),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_dice_create)) }
            }
            FirstRunStep.PhraseShow -> {
                Text(stringResource(R.string.fr_phrase_title, state.phrase.size), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.fr_phrase_warning))
                HeroCard {
                    state.phrase.chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEachIndexed { col, word ->
                                Text(
                                    "${row * 2 + col + 1}. $word",
                                    fontFamily = ElektronMono,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Button(onClick = { state = FirstRunFlow.next(state) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.fr_phrase_written))
                }
            }
            FirstRunStep.PhraseQuiz -> {
                val quiz = remember { BackupQuiz(state.phrase, SecureRandom()) }
                var answers by remember { mutableStateOf(List(quiz.positions.size) { "" }) }
                var wrong by remember { mutableStateOf(false) }
                Text(stringResource(R.string.fr_quiz_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.fr_quiz_body))
                quiz.positions.forEachIndexed { i, position ->
                    OutlinedTextField(
                        value = answers[i],
                        onValueChange = { v -> answers = answers.toMutableList().also { it[i] = v }; wrong = false },
                        label = { Text(stringResource(R.string.fr_quiz_word, position + 1)) },
                        singleLine = true,
                        isError = wrong,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (wrong) Text(stringResource(R.string.fr_quiz_wrong), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { if (quiz.check(answers)) state = FirstRunFlow.next(state) else wrong = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_continue)) }
                TextButton(onClick = { FirstRunFlow.back(state)?.let { state = it } }) {
                    Text(stringResource(R.string.fr_quiz_show_again))
                }
            }
            FirstRunStep.PassphraseNew, FirstRunStep.PassphraseRestore -> {
                PassphraseStep(
                    value = passphrase,
                    onValue = { passphrase = it },
                    failure = state.failure,
                    restore = state.restore,
                    onSubmit = { submit(recover = state.restore) },
                )
            }
            FirstRunStep.RestoreEnter -> {
                var text by remember { mutableStateOf("") }
                val words = Bip39.parse(text)
                val valid = bip39.validate(words)
                Text(stringResource(R.string.fr_restore_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.fr_restore_body))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    isError = words.size >= 12 && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (words.size >= 12 && !valid) {
                    Text(stringResource(R.string.fr_restore_invalid), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { state = FirstRunFlow.next(state.copy(phrase = words)) },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_continue)) }
            }
            FirstRunStep.Creating, FirstRunStep.Restoring -> {
                val snapshot = NodeHost.latest
                CircularProgressIndicator()
                Text(
                    stringResource(
                        if (state.step == FirstRunStep.Creating) R.string.fr_creating else R.string.fr_restoring,
                    ),
                )
                if (state.step == FirstRunStep.Restoring && snapshot?.headline !is Headline.Synchronized) {
                    Text(stringResource(R.string.fr_restore_waits))
                }
            }
            FirstRunStep.Done -> Unit
        }
        }
        }
    }
    }
}

@Composable
private fun PassphraseStep(
    value: String,
    onValue: (String) -> Unit,
    failure: BootstrapFailure?,
    restore: Boolean,
    onSubmit: () -> Unit,
) {
    var confirm by remember { mutableStateOf("") }
    val strength = PassphraseCheck.strength(value)
    Text(stringResource(R.string.fr_pass_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.fr_pass_body))
    SecretField(
        value = value,
        onValueChange = onValue,
        label = { Text(stringResource(R.string.fr_pass_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(
            when (strength) {
                PassphraseStrength.TooShort -> R.string.fr_pass_short
                PassphraseStrength.Weak -> R.string.fr_pass_weak
                PassphraseStrength.Fair -> R.string.fr_pass_fair
                PassphraseStrength.Strong -> R.string.fr_pass_strong
            },
            PassphraseCheck.MIN_LENGTH,
        ),
    )
    SecretField(
        value = confirm,
        onValueChange = { confirm = it },
        label = { Text(stringResource(R.string.fr_pass_confirm)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = confirm.isNotEmpty() && confirm != value,
        modifier = Modifier.fillMaxWidth(),
    )
    failure?.let { Text(stringResource(failureText(it)), color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = onSubmit,
        enabled = PassphraseCheck.acceptable(value) && value == confirm,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(if (restore) R.string.fr_restore_action else R.string.fr_create_action)) }
}

private fun failureText(failure: BootstrapFailure): Int = when (failure) {
    BootstrapFailure.NodeNotReady -> R.string.fr_err_node
    BootstrapFailure.WalletExists -> R.string.fr_err_exists
    BootstrapFailure.RecoveryUnsupported -> R.string.fr_err_recovery
    BootstrapFailure.InvalidPhrase -> R.string.fr_restore_invalid
    BootstrapFailure.WeakPassphrase -> R.string.fr_pass_weak
    BootstrapFailure.WrongPassphrase -> R.string.send_err_passphrase
    BootstrapFailure.ImportFailed, BootstrapFailure.Unknown -> R.string.fr_err_unknown
}
