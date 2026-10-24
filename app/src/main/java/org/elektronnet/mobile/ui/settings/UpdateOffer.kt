package org.elektronnet.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import org.elektronnet.mobile.R
import org.elektronnet.mobile.node.ReleaseKind
import org.elektronnet.mobile.node.UpdateCheck
import org.elektronnet.mobile.node.UpdateResult
import org.elektronnet.mobile.ui.theme.UpdateState

@Composable
internal fun UpdateOffer(offer: UpdateResult.Newer) {
    val context = LocalContext.current
    Text(stringResource(R.string.update_available, offer.tag), color = MaterialTheme.colorScheme.primary)
    when (offer.kind) {
        ReleaseKind.Test -> Text(stringResource(R.string.update_note_test), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ReleaseKind.Candidate -> Text(stringResource(R.string.update_note_candidate), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ReleaseKind.Final -> Unit
    }
    TextButton(onClick = {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(UpdateCheck.RELEASE_URL + offer.tag)))
    }) { Text(stringResource(R.string.update_open)) }
}

// shown on the wallet screen until the user chooses Later, a newer release brings it back
@Composable
fun UpdateBanner() {
    val offer = UpdateState.visibleOffer ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        UpdateOffer(offer)
        Row { TextButton(onClick = { UpdateState.dismiss() }) { Text(stringResource(R.string.update_dismiss)) } }
    }
}
