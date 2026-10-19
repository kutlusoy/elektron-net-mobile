@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.elektronnet.mobile.ui.wallets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.GlassCard
import org.elektronnet.mobile.ui.theme.ElektronMono
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.ui.tools.ToolPage
import org.elektronnet.mobile.ui.tools.copyText
import org.elektronnet.mobile.ui.tools.pasteText
import org.elektronnet.mobile.wallet.AddressBook
import org.elektronnet.mobile.wallet.Contact
import org.elektronnet.mobile.wallet.ContactProblem
import org.elektronnet.mobile.wallet.ContactResult

@Composable
fun ContactsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val book = NodeHost.addressBook()
    var rows by remember { mutableStateOf(book?.all().orEmpty()) }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var adding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    ToolPage(stringResource(R.string.contacts_title), onBack) {
        Hint(stringResource(R.string.contacts_hint))
        if (rows.isEmpty()) Text(stringResource(R.string.contacts_empty))
        rows.forEach { contact ->
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                    Text(contact.address, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.layout.FlowRow {
                        TextButton(onClick = { copyText(context, "address", contact.address) }) { Text(stringResource(R.string.action_copy)) }
                        TextButton(onClick = { editing = contact }) { Text(stringResource(R.string.wallets_rename)) }
                        TextButton(onClick = {
                            book?.remove(contact.address)
                            rows = book?.all().orEmpty()
                        }) { Text(stringResource(R.string.wallets_remove), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.contacts_add)) }
    }
    if (adding || editing != null) {
        ContactDialog(
            book = book,
            existing = editing,
            onDone = {
                adding = false
                editing = null
                rows = book?.all().orEmpty()
            },
            onDismiss = { adding = false; editing = null },
        )
    }
}

@Composable
private fun ContactDialog(book: AddressBook?, existing: Contact?, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var address by remember { mutableStateOf(existing?.address.orEmpty()) }
    var problem by remember { mutableStateOf<ContactProblem?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.contacts_add else R.string.wallets_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(AddressBook.MAX_NAME) }, singleLine = true, label = { Text(stringResource(R.string.contacts_name)) })
                OutlinedTextField(value = address, onValueChange = { address = it }, minLines = 2, label = { Text(stringResource(R.string.send_recipient_hint)) })
                OutlinedButton(onClick = { address = pasteText(context).trim() }) { Text(stringResource(R.string.send_paste)) }
                problem?.let { Text(stringResource(contactProblemText(it)), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val result = book?.save(name, address, replacing = existing?.address)) {
                    ContactResult.Saved -> onDone()
                    is ContactResult.Refused -> problem = result.problem
                    null -> problem = ContactProblem.InvalidAddress
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ContactPickerDialog(onPick: (Contact) -> Unit, onDismiss: () -> Unit) {
    val rows = NodeHost.addressBook()?.all().orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contacts_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rows.isEmpty()) Text(stringResource(R.string.contacts_empty))
                rows.forEach { contact ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { onPick(contact) }) {
                        Text(contact.name, style = MaterialTheme.typography.titleSmall)
                        Text(contact.address.take(12) + "…" + contact.address.takeLast(8), fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun contactProblemText(problem: ContactProblem): Int = when (problem) {
    ContactProblem.EmptyName -> R.string.contacts_err_name
    ContactProblem.NameTooLong -> R.string.contacts_err_name
    ContactProblem.InvalidAddress -> R.string.contacts_err_address
    ContactProblem.Duplicate -> R.string.contacts_err_duplicate
    ContactProblem.Full -> R.string.contacts_err_full
}
