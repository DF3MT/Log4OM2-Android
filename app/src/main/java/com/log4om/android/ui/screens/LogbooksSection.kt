package com.log4om.android.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.log4om.android.R
import com.log4om.android.ui.components.DropdownField
import com.log4om.android.ui.components.LabeledTextField
import com.log4om.android.ui.components.SectionHeader
import com.log4om.android.ui.viewmodel.LogbookViewModel
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LogbooksSection(viewModel: LogbookViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSync by remember { mutableStateOf<String?>(null) }
    val draft = state.draft

    LaunchedEffect(state.exportAdif) {
        val adif = state.exportAdif ?: return@LaunchedEffect
        val name = "logbook_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.adi"
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(adif, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_title))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, context.getString(R.string.export_share_title)))
        viewModel.clearExport()
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_logbook)) },
            text = { Text(stringResource(R.string.confirm_delete_logbook)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    confirmSync?.let { service ->
        AlertDialog(
            onDismissRequest = { confirmSync = null },
            title = { Text(if (service == "qrz") stringResource(R.string.sync_qrz) else stringResource(R.string.sync_eqsl)) },
            text = { Text(stringResource(R.string.sync_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSync = null
                    viewModel.sync(service)
                }) { Text(stringResource(R.string.sync_go)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSync = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    SectionHeader(stringResource(R.string.section_logbooks))
    Text(
        stringResource(R.string.logbooks_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val ids = listOf("") + state.books.map { it.id }
    DropdownField(
        label = stringResource(R.string.section_logbooks),
        value = draft.id,
        options = ids,
        onSelect = { id ->
            if (id.isBlank()) viewModel.startNew()
            else state.books.find { it.id == id }?.let { viewModel.select(it) }
        },
        optionLabel = { id ->
            if (id.isBlank()) stringResource(R.string.logbook_new)
            else {
                val book = state.books.find { it.id == id }
                val name = book?.displayName.orEmpty()
                val call = book?.stationCallsign.orEmpty()
                val mark = if (book?.active == true) " · " + stringResource(R.string.logbook_active) else ""
                listOf(name, call).filter { it.isNotBlank() }.joinToString(" · ") + mark
            }
        }
    )
    if (draft.id.isNotBlank() && state.active) {
        Text(stringResource(R.string.logbook_active), style = MaterialTheme.typography.labelLarge)
    }
    LabeledTextField(
        label = stringResource(R.string.logbook_name),
        value = draft.displayName,
        onValueChange = { v -> viewModel.updateDraft { it.copy(displayName = v) } }
    )
    LabeledTextField(
        label = stringResource(R.string.logbook_callsign),
        value = draft.stationCallsign,
        onValueChange = { v -> viewModel.updateDraft { it.copy(stationCallsign = v.uppercase()) } }
    )
    LabeledTextField(
        label = stringResource(R.string.db_host),
        value = draft.host,
        onValueChange = { v -> viewModel.updateDraft { it.copy(host = v) } }
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledTextField(
            label = stringResource(R.string.db_port),
            value = draft.port.toString(),
            onValueChange = { v ->
                viewModel.updateDraft { it.copy(port = v.filter(Char::isDigit).take(5).toIntOrNull() ?: 3306) }
            },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        LabeledTextField(
            label = stringResource(R.string.db_name),
            value = draft.database,
            onValueChange = { v -> viewModel.updateDraft { it.copy(database = v) } },
            modifier = Modifier.weight(1f)
        )
    }
    LabeledTextField(
        label = stringResource(R.string.db_user),
        value = draft.username,
        onValueChange = { v -> viewModel.updateDraft { it.copy(username = v) } }
    )
    LabeledTextField(
        label = stringResource(R.string.db_password),
        value = draft.password,
        onValueChange = { v -> viewModel.updateDraft { it.copy(password = v) } },
        isPassword = true,
        supportingText = if (state.passwordSet) stringResource(R.string.password_keep) else null
    )
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Switch(
            checked = draft.sslEnabled,
            onCheckedChange = { v -> viewModel.updateDraft { it.copy(sslEnabled = v) } }
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.db_ssl))
    }
    Text(
        stringResource(R.string.qrz_logbook_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LabeledTextField(
        label = stringResource(R.string.qrz_logbook_key),
        value = draft.qrzLogbookKey,
        onValueChange = { v -> viewModel.updateDraft { it.copy(qrzLogbookKey = v) } },
        isPassword = true,
        supportingText = if (state.qrzKeySet) stringResource(R.string.password_keep) else null
    )
    LabeledTextField(
        label = stringResource(R.string.eqsl_user),
        value = draft.eqslUser,
        onValueChange = { v -> viewModel.updateDraft { it.copy(eqslUser = v) } }
    )
    LabeledTextField(
        label = stringResource(R.string.eqsl_password),
        value = draft.eqslPassword,
        onValueChange = { v -> viewModel.updateDraft { it.copy(eqslPassword = v) } },
        isPassword = true,
        supportingText = if (state.eqslPasswordSet) stringResource(R.string.password_keep) else null
    )
    if (state.busy) {
        CircularProgressIndicator(Modifier.height(24.dp))
    }
    Button(onClick = viewModel::save, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        if (draft.id.isBlank()) IconAdd()
        Text(stringResource(if (draft.id.isBlank()) R.string.logbook_add else R.string.save))
    }
    OutlinedButton(
        onClick = viewModel::testConnection,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.test_connection)) }
    if (draft.id.isNotBlank() && !state.active) {
        Button(onClick = viewModel::activate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.logbook_use))
        }
    }
    if (draft.id.isNotBlank()) {
        OutlinedButton(onClick = viewModel::export, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.export_all))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { confirmSync = "qrz" },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.sync_qrz)) }
            OutlinedButton(
                onClick = { confirmSync = "eqsl" },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.sync_eqsl)) }
        }
        OutlinedButton(
            onClick = { confirmDelete = true },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.delete_logbook)) }
    }
}

@Composable
private fun IconAdd() {
    androidx.compose.material3.Icon(Icons.Default.Add, null)
    Spacer(Modifier.width(8.dp))
}
