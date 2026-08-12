package com.log4om.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.log4om.android.R
import com.log4om.android.data.model.LogFilter
import com.log4om.android.data.model.Qso
import com.log4om.android.ui.components.DropdownField
import com.log4om.android.ui.components.LabeledTextField
import com.log4om.android.ui.viewmodel.LogUiState
import com.log4om.android.ui.viewmodel.LogViewModel
import com.log4om.android.util.AmateurRadio
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
private val CHIP_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val EXPORT_NAME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

private enum class PendingExport { Share, Save }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogListScreen(
    viewModel: LogViewModel,
    onQsoClick: (Qso) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val deleteMsg by viewModel.deleteMessage.collectAsState()
    val message by viewModel.message.collectAsState()
    val exportAdif by viewModel.exportAdif.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<Qso?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    var pendingAdifForSave by remember { mutableStateOf<String?>(null) }

    val deleteMsgText = deleteMsg?.asString()
    LaunchedEffect(deleteMsgText) {
        deleteMsgText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearDeleteMessage()
        }
    }
    val messageText = message?.asString()
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val content = pendingAdifForSave
        pendingAdifForSave = null
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.export_done))
            }
        }
    }

    LaunchedEffect(exportAdif, pendingExport) {
        val adif = exportAdif ?: return@LaunchedEffect
        val action = pendingExport ?: return@LaunchedEffect
        when (action) {
            PendingExport.Share -> {
                val name = "log4om_export_${java.time.LocalDateTime.now().format(EXPORT_NAME_FMT)}.adi"
                val dir = File(context.cacheDir, "export").apply { mkdirs() }
                val file = File(dir, name)
                file.writeText(adif, Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_title))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(share, context.getString(R.string.export_share_title))
                )
                viewModel.clearExportAdif()
                pendingExport = null
            }
            PendingExport.Save -> {
                pendingAdifForSave = adif
                val name = "log4om_export_${java.time.LocalDateTime.now().format(EXPORT_NAME_FMT)}.adi"
                createDocLauncher.launch(name)
                viewModel.clearExportAdif()
                pendingExport = null
            }
        }
    }

    deleteTarget?.let { qso ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_qso_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_qso_message,
                        qso.callsign,
                        qso.qsodate.format(DATE_FMT)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteQso(qso.qsoid)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            initial = filter,
            onDismiss = { showFilterSheet = false },
            onApply = {
                viewModel.applyFilter(it)
                showFilterSheet = false
            },
            onReset = {
                viewModel.clearFilter()
                showFilterSheet = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.selection_count, selectedIds.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::exitSelectionMode) {
                            Icon(Icons.Default.Close, stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::selectAllMatchingFilter) {
                            Icon(Icons.Default.SelectAll, stringResource(R.string.select_all))
                        }
                        IconButton(
                            onClick = {
                                pendingExport = PendingExport.Share
                                viewModel.prepareExport()
                            },
                            enabled = selectedIds.isNotEmpty() && !isExporting
                        ) {
                            Icon(Icons.Default.Share, stringResource(R.string.share))
                        }
                        IconButton(
                            onClick = {
                                pendingExport = PendingExport.Save
                                viewModel.prepareExport()
                            },
                            enabled = selectedIds.isNotEmpty() && !isExporting
                        ) {
                            Icon(Icons.Default.SaveAlt, stringResource(R.string.save_as))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.nav_log))
                            if (uiState is LogUiState.Success) {
                                Text(
                                    stringResource(
                                        R.string.total_qsos,
                                        (uiState as LogUiState.Success).totalCount
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFilterSheet = true }) {
                            BadgedBox(
                                badge = {
                                    if (filter.isActive) {
                                        Badge()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.FilterList, stringResource(R.string.filter))
                            }
                        }
                        IconButton(onClick = { viewModel.enterSelectionMode() }) {
                            Icon(Icons.Default.Checklist, stringResource(R.string.select))
                        }
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (filter.isActive && !selectionMode) {
                ActiveFilterChips(
                    filter = filter,
                    onClear = viewModel::clearFilter,
                    onOpen = { showFilterSheet = true }
                )
            }
            if (isExporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Box(Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is LogUiState.Loading -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    is LogUiState.Error -> {
                        Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.connection_error),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                state.message.asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = viewModel::refresh) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    is LogUiState.Success -> {
                        if (state.qsos.isEmpty()) {
                            Column(
                                Modifier.align(Alignment.Center).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.no_qsos),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val listState = rememberLazyListState()
                            LaunchedEffect(listState, state.qsos.size, state.endReached, state.isLoadingMore) {
                                snapshotFlow {
                                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                    last >= state.qsos.size - 5
                                }
                                    .distinctUntilChanged()
                                    .filter { it && !state.endReached && !state.isLoadingMore }
                                    .collect { viewModel.loadMore() }
                            }
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(state.qsos, key = { it.qsoid }) { qso ->
                                    QsoListItem(
                                        qso = qso,
                                        selected = qso.qsoid in selectedIds,
                                        selectionMode = selectionMode,
                                        onClick = {
                                            if (selectionMode) {
                                                viewModel.toggleSelection(qso.qsoid)
                                            } else {
                                                onQsoClick(qso)
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                viewModel.enterSelectionMode(qso.qsoid)
                                            } else {
                                                viewModel.toggleSelection(qso.qsoid)
                                            }
                                        },
                                        onDelete = { deleteTarget = qso }
                                    )
                                }
                                if (state.isLoadingMore) {
                                    item(key = "loader") {
                                        Box(
                                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(28.dp),
                                                strokeWidth = 3.dp
                                            )
                                        }
                                    }
                                } else if (state.endReached && state.qsos.isNotEmpty()) {
                                    item(key = "end") {
                                        Text(
                                            stringResource(R.string.list_end),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterChips(
    filter: LogFilter,
    onClear: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (filter.callsign.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_call, filter.callsign)) }
            )
        }
        if (filter.band.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_band, filter.band)) }
            )
        }
        if (filter.mode.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_mode, filter.mode)) }
            )
        }
        filter.dateFrom?.let {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_from, it.format(CHIP_DATE_FMT))) }
            )
        }
        filter.dateTo?.let {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_to, it.format(CHIP_DATE_FMT))) }
            )
        }
        if (filter.country.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_country, filter.country)) }
            )
        }
        if (filter.dxcc.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_dxcc, filter.dxcc)) }
            )
        }
        if (filter.sotaRef.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_sota, filter.sotaRef)) }
            )
        }
        if (filter.iota.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_iota, filter.iota)) }
            )
        }
        if (filter.potaRef.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_pota, filter.potaRef)) }
            )
        }
        if (filter.wwffRef.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_wwff, filter.wwffRef)) }
            )
        }
        if (filter.cotaRef.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onOpen,
                label = { Text(stringResource(R.string.filter_chip_cota, filter.cotaRef)) }
            )
        }
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.filter_reset))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    initial: LogFilter,
    onDismiss: () -> Unit,
    onApply: (LogFilter) -> Unit,
    onReset: () -> Unit
) {
    var callsign by remember { mutableStateOf(initial.callsign) }
    var band by remember { mutableStateOf(initial.band) }
    var mode by remember { mutableStateOf(initial.mode) }
    var dateFrom by remember { mutableStateOf(initial.dateFrom) }
    var dateTo by remember { mutableStateOf(initial.dateTo) }
    var country by remember { mutableStateOf(initial.country) }
    var dxcc by remember { mutableStateOf(initial.dxcc) }
    var sotaRef by remember { mutableStateOf(initial.sotaRef) }
    var iota by remember { mutableStateOf(initial.iota) }
    var potaRef by remember { mutableStateOf(initial.potaRef) }
    var wwffRef by remember { mutableStateOf(initial.wwffRef) }
    var cotaRef by remember { mutableStateOf(initial.cotaRef) }
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.filter_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.filter_wildcard_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabeledTextField(
                label = stringResource(R.string.callsign),
                value = callsign,
                onValueChange = { callsign = it.uppercase() }
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownField(
                    label = stringResource(R.string.band),
                    value = band,
                    options = listOf("") + AmateurRadio.BANDS,
                    onSelect = { band = it },
                    modifier = Modifier.weight(1f),
                    optionLabel = { if (it.isBlank()) stringResource(R.string.filter_all_bands) else it }
                )
                DropdownField(
                    label = stringResource(R.string.mode),
                    value = mode,
                    options = listOf("") + AmateurRadio.MODES,
                    onSelect = { mode = it },
                    modifier = Modifier.weight(1f),
                    optionLabel = { if (it.isBlank()) stringResource(R.string.filter_all_modes) else it }
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterDateField(
                    label = stringResource(R.string.filter_date_from),
                    value = dateFrom,
                    onClick = { pickingFrom = true },
                    onClear = { dateFrom = null },
                    modifier = Modifier.weight(1f)
                )
                FilterDateField(
                    label = stringResource(R.string.filter_date_to),
                    value = dateTo,
                    onClick = { pickingTo = true },
                    onClear = { dateTo = null },
                    modifier = Modifier.weight(1f)
                )
            }
            LabeledTextField(
                label = stringResource(R.string.country),
                value = country,
                onValueChange = { country = it }
            )
            LabeledTextField(
                label = stringResource(R.string.dxcc),
                value = dxcc,
                onValueChange = { dxcc = it.filter(Char::isDigit).take(4) },
                keyboardType = KeyboardType.Number
            )
            LabeledTextField(
                label = stringResource(R.string.sota_ref),
                value = sotaRef,
                onValueChange = { sotaRef = it.uppercase() }
            )
            LabeledTextField(
                label = stringResource(R.string.iota),
                value = iota,
                onValueChange = { iota = it.uppercase() }
            )
            LabeledTextField(
                label = stringResource(R.string.pota_ref),
                value = potaRef,
                onValueChange = { potaRef = it.uppercase() }
            )
            LabeledTextField(
                label = stringResource(R.string.wwff_ref),
                value = wwffRef,
                onValueChange = { wwffRef = it.uppercase() }
            )
            LabeledTextField(
                label = stringResource(R.string.cota_ref),
                value = cotaRef,
                onValueChange = { cotaRef = it.uppercase() }
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.filter_reset))
                }
                Button(
                    onClick = {
                        onApply(
                            LogFilter(
                                callsign = callsign.trim(),
                                band = band.trim(),
                                mode = mode.trim(),
                                dateFrom = dateFrom,
                                dateTo = dateTo,
                                country = country.trim(),
                                dxcc = dxcc.trim(),
                                sotaRef = sotaRef.trim(),
                                iota = iota.trim(),
                                potaRef = potaRef.trim(),
                                wwffRef = wwffRef.trim(),
                                cotaRef = cotaRef.trim()
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.filter_apply))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickingFrom) {
        DatePickDialog(
            initial = dateFrom ?: LocalDate.now(),
            onDismiss = { pickingFrom = false },
            onConfirm = {
                dateFrom = it
                pickingFrom = false
            }
        )
    }
    if (pickingTo) {
        DatePickDialog(
            initial = dateTo ?: LocalDate.now(),
            onDismiss = { pickingTo = false },
            onConfirm = {
                dateTo = it
                pickingTo = false
            }
        )
    }
}

@Composable
private fun FilterDateField(
    label: String,
    value: LocalDate?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value?.format(CHIP_DATE_FMT).orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier.clickable(onClick = onClick),
        trailingIcon = {
            Row {
                if (value != null) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, stringResource(R.string.clear_date))
                    }
                }
                IconButton(onClick = onClick) {
                    Icon(Icons.Default.DateRange, label)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis ?: return@TextButton
                val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                onConfirm(date)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QsoListItem(
    qso: Qso,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() }
                )
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = qso.callsign,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(qso.band, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(qso.mode, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row {
                    if (qso.name.isNotBlank()) {
                        Text(
                            qso.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (qso.country.isNotBlank()) {
                            Text(
                                " · ${qso.country}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (qso.country.isNotBlank()) {
                        Text(
                            qso.country,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        qso.qsodate.format(DATE_FMT) + " UTC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (qso.freq > 0) {
                        Text(
                            " · ${qso.freq} MHz",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "↑${qso.rstsent} ↓${qso.rstrcvd}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (!selectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.options))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
