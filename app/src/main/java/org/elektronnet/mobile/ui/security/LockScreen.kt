package org.elektronnet.mobile.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ui.BrandBackground
import org.elektronnet.mobile.ui.BrandHeader
import org.elektronnet.mobile.ui.SecureWindow

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    SecureWindow()
    LaunchedEffect(Unit) { onUnlock() }
    BrandBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandHeader(compact = false)
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge)
            Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
