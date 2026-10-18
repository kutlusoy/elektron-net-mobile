package org.elektronnet.mobile.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.elektronnet.mobile.node.NodeFailure
import org.elektronnet.mobile.node.NodeProfile
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeStatus
import org.elektronnet.mobile.policy.PolicyDecision
import org.elektronnet.mobile.policy.PolicyReason
import org.elektronnet.mobile.policy.PolicySettings
import org.elektronnet.mobile.runtime.Headline
import org.elektronnet.mobile.runtime.RuntimeSnapshot
import org.elektronnet.mobile.ui.model.HomeUiModel
import org.elektronnet.mobile.ui.model.NodeTabModel
import org.elektronnet.mobile.ui.node.NodeScreen
import org.elektronnet.mobile.ui.settings.SettingsScreen
import org.elektronnet.mobile.ui.theme.ElektronTheme
import org.elektronnet.mobile.ui.wallet.WalletScreen

private fun snapshot(headline: Headline, state: NodeState, withStatus: Boolean = true) = RuntimeSnapshot(
    PolicyDecision(true, headline !is Headline.Paused, NodeProfile.Balanced, false, false, emptySet()),
    state,
    if (withStatus) NodeStatus("main", 120_000, 270_000, 0.44, true, true, 197_281, 7, "/Elektron:5/") else null,
    headline,
)

private val syncing = snapshot(Headline.Syncing(120_000, 270_000, 0.44, 7), NodeState.Running)
private val synced = snapshot(Headline.Synchronized(270_000, 9), NodeState.Running)
private val paused = snapshot(Headline.Paused(setOf(PolicyReason.MeteredNotAllowed)), NodeState.Running)
private val offline = snapshot(Headline.Paused(setOf(PolicyReason.NoNetwork)), NodeState.Running)
private val crashed = snapshot(Headline.Crashed(NodeFailure.DiskFull), NodeState.Crashed(1, NodeFailure.DiskFull), withStatus = false)
private val stopped = snapshot(Headline.Stopped, NodeState.Stopped, withStatus = false)

@Composable
private fun Wallet(snapshot: RuntimeSnapshot?, balance: String? = null) {
    ElektronTheme {
        WalletScreen(HomeUiModel.from(snapshot?.headline), balance, onOpenNode = {}, onReceive = {}, onSend = {})
    }
}

@Composable
private fun Node(snapshot: RuntimeSnapshot?, userPaused: Boolean = false) {
    ElektronTheme {
        NodeScreen(NodeTabModel.from(snapshot, userPaused), onPause = {}, onResume = {}, onStop = {}, onStart = {})
    }
}

@Preview(name = "Wallet syncing", showBackground = true) @Composable private fun WalletSyncing() = Wallet(syncing)

@Preview(name = "Wallet synced", showBackground = true) @Composable private fun WalletSynced() = Wallet(synced, balance = "12.5")

@Preview(name = "Wallet paused", showBackground = true) @Composable private fun WalletPaused() = Wallet(paused)

@Preview(name = "Wallet offline", showBackground = true) @Composable private fun WalletOffline() = Wallet(offline)

@Preview(name = "Wallet problem", showBackground = true) @Composable private fun WalletProblem() = Wallet(crashed)

@Preview(name = "Wallet stopped", showBackground = true) @Composable private fun WalletStopped() = Wallet(stopped)

@Preview(name = "Node syncing", showBackground = true) @Composable private fun NodeSyncing() = Node(syncing)

@Preview(name = "Node synced", showBackground = true) @Composable private fun NodeSynced() = Node(synced)

@Preview(name = "Node paused by user", showBackground = true) @Composable private fun NodePausedByUser() = Node(
    snapshot(Headline.Paused(setOf(PolicyReason.UserPaused)), NodeState.Running),
    userPaused = true,
)

@Preview(name = "Node paused", showBackground = true) @Composable private fun NodePaused() = Node(paused)

@Preview(name = "Node problem", showBackground = true) @Composable private fun NodeProblem() = Node(crashed)

@Preview(name = "Node stopped", showBackground = true) @Composable private fun NodeStopped() = Node(stopped)

@Preview(name = "Settings", showBackground = true) @Composable private fun SettingsPreview() = ElektronTheme {
    SettingsScreen(PolicySettings(), onAllowMetered = {}, onChargingOnly = {}, onBackground = {})
}

@Preview(name = "Settings large font", showBackground = true, fontScale = 2f) @Composable private fun SettingsLargeFont() = ElektronTheme {
    SettingsScreen(PolicySettings(chargingOnly = true), onAllowMetered = {}, onChargingOnly = {}, onBackground = {})
}
