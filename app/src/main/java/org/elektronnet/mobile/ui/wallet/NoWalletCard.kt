package org.elektronnet.mobile.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ui.GlassCard

@Composable
fun NoWalletCard(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.nowallet_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.nowallet_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.nowallet_create)) }
            }
        }
    }
}
