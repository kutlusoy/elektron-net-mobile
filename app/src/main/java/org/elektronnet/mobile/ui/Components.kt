package org.elektronnet.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ui.model.NodeChip
import org.elektronnet.mobile.ui.theme.LocalEffectLevel
import org.elektronnet.mobile.ui.theme.LocalStatus
import org.elektronnet.mobile.ui.theme.accentGlow

@Composable
fun TestnetBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalStatus.current.warning)
            .statusBarsPadding()
            .padding(8.dp),
    ) {
        Text(stringResource(R.string.testnet_bar), color = MaterialTheme.colorScheme.background)
    }
}

@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .accentGlow(LocalEffectLevel.current, MaterialTheme.colorScheme.primary)
            .clip(RoundedCornerShape(28.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))))
            .border(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))), RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun PillNavBar(items: List<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (label, icon, index) ->
            val active = index == selected
            val shape = RoundedCornerShape(22.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (active) primary.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.dp, if (active) primary.copy(alpha = 0.75f) else Color.Transparent, shape)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun chipColor(chip: NodeChip): Color {
    val status = LocalStatus.current
    return when (chip) {
        NodeChip.Synced -> status.success
        NodeChip.Syncing -> MaterialTheme.colorScheme.primary
        NodeChip.Paused, NodeChip.Offline -> status.warning
        NodeChip.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
        NodeChip.Problem -> status.danger
    }
}

@Composable
fun chipLabel(chip: NodeChip): String = stringResource(
    when (chip) {
        NodeChip.Synced -> R.string.chip_synced
        NodeChip.Syncing -> R.string.chip_syncing
        NodeChip.Paused -> R.string.chip_paused
        NodeChip.Offline -> R.string.chip_offline
        NodeChip.Stopped -> R.string.chip_stopped
        NodeChip.Problem -> R.string.chip_problem
    },
)

@Composable
fun NodeChipRow(chip: NodeChip, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(chipColor(chip))
        Text(chipLabel(chip), style = MaterialTheme.typography.labelLarge)
    }
}
