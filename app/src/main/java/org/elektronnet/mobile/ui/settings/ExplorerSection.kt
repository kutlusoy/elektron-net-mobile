package org.elektronnet.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.explorer.ExplorerList
import org.elektronnet.mobile.ui.theme.ElektronMono
import org.elektronnet.mobile.ui.tools.Hint

@Composable
internal fun ExplorerSection(urls: List<String>, onChange: (List<String>) -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    Text(stringResource(R.string.explorer_title), style = MaterialTheme.typography.titleMedium)
    Hint(stringResource(R.string.explorer_hint))
    if (urls.isEmpty()) Text(stringResource(R.string.explorer_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
    urls.forEachIndexed { index, url ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(url, fontFamily = ElektronMono, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index == 0) Text(stringResource(R.string.explorer_first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                else TextButton(onClick = { onChange(listOf(url) + urls.filter { it != url }) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.explorer_make_first)) }
                TextButton(onClick = { editing = url }) { Text(stringResource(R.string.wallets_rename)) }
                TextButton(onClick = { onChange(urls - url) }) { Text(stringResource(R.string.explorer_remove)) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { adding = true }) { Text(stringResource(R.string.explorer_add)) }
        if (urls != listOf(ExplorerList.DEFAULT_URL)) OutlinedButton(onClick = { onChange(listOf(ExplorerList.DEFAULT_URL)) }) { Text(stringResource(R.string.explorer_reset)) }
    }
    Hint(stringResource(R.string.explorer_privacy))
    if (adding) {
        UrlDialog(initial = "", others = urls, replacing = null, onDismiss = { adding = false }) { onChange(urls + it); adding = false }
    }
    editing?.let { current ->
        UrlDialog(initial = current, others = urls, replacing = current, onDismiss = { editing = null }) { new ->
            onChange(urls.map { if (it == current) new else it })
            editing = null
        }
    }
}

@Composable
private fun UrlDialog(initial: String, others: List<String>, replacing: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val result = if (text.isBlank()) null else ExplorerList.check(text, others, replacing)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (replacing == null) R.string.explorer_add else R.string.wallets_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.explorer_url_label)) },
                    singleLine = true,
                    isError = result != null && result !is ExplorerList.Check.Ok,
                    modifier = Modifier.fillMaxWidth(),
                )
                val error = when (result) {
                    ExplorerList.Check.Invalid -> R.string.explorer_err_invalid
                    ExplorerList.Check.Insecure -> R.string.explorer_err_insecure
                    ExplorerList.Check.Duplicate -> R.string.explorer_err_duplicate
                    ExplorerList.Check.Full -> R.string.explorer_err_full
                    else -> null
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { (result as? ExplorerList.Check.Ok)?.let { onSave(it.url) } }, enabled = result is ExplorerList.Check.Ok) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
