package org.elektronnet.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.model.TelemetryFormat
import org.elektronnet.mobile.ui.tools.Hint

private val LOG_DAYS = listOf(1, 3, 7, 14, 30)
private val LOG_MB = listOf(1, 5, 10, 25, 50)

@Composable
fun LogSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = NodeHost.store ?: return
    var days by remember { mutableStateOf(store.logDays) }
    var megabytes by remember { mutableStateOf(store.logMaxMb) }
    var freed by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    val dayIndex = LOG_DAYS.indexOfFirst { it >= days }.let { if (it < 0) LOG_DAYS.lastIndex else it }
    val sizeIndex = LOG_MB.indexOfFirst { it >= megabytes }.let { if (it < 0) LOG_MB.lastIndex else it }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepSlider(
            title = R.string.settings_log_days,
            steps = LOG_DAYS.size,
            index = dayIndex,
            label = stringResource(R.string.settings_log_days_value, LOG_DAYS[dayIndex]),
            labelFor = { i -> context.getString(R.string.settings_log_days_value, LOG_DAYS[i]) },
            onCommit = { i -> days = LOG_DAYS[i]; store.logDays = LOG_DAYS[i] },
        )
        StepSlider(
            title = R.string.settings_log_size,
            steps = LOG_MB.size,
            index = sizeIndex,
            label = stringResource(R.string.settings_log_size_value, LOG_MB[sizeIndex]),
            labelFor = { i -> context.getString(R.string.settings_log_size_value, LOG_MB[i]) },
            onCommit = { i -> megabytes = LOG_MB[i]; store.logMaxMb = LOG_MB[i] },
        )
        Hint(stringResource(R.string.settings_log_hint))
        OutlinedButton(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { NodeHost.cleanLogs() }.getOrNull() }
                    freed = result?.freedBytes ?: 0L
                    busy = false
                    withContext(Dispatchers.IO) { runCatching { NodeHost.refreshStorage() } }
                }
            },
        ) { Text(stringResource(R.string.settings_log_clean)) }
        freed?.let { Text(stringResource(R.string.settings_log_freed, TelemetryFormat.bytes(it)), color = MaterialTheme.colorScheme.primary) }
    }
}
