package org.elektronnet.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.service.NodeNotification
import org.elektronnet.mobile.ui.theme.LocalEffectLevel
import org.elektronnet.mobile.ui.theme.LocalStatus
import org.elektronnet.mobile.ui.theme.accentGlow

@Composable
fun HomeScreen(onStart: () -> Unit, onStop: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            if (ChainConfig.isTestnet) TestnetBar()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .accentGlow(LocalEffectLevel.current, MaterialTheme.colorScheme.primary)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.home_node_section), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.chain_label, ChainConfig.chain))
                        Text(
                            NodeNotification.text(LocalContext.current, NodeHost.latest?.headline),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onStart) { Text(stringResource(R.string.action_start)) }
                            OutlinedButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestnetBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalStatus.current.warning)
            .padding(8.dp),
    ) {
        Text(stringResource(R.string.testnet_bar), color = MaterialTheme.colorScheme.background)
    }
}
