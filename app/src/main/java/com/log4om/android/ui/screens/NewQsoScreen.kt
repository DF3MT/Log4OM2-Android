package com.log4om.android.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.log4om.android.R
import com.log4om.android.data.model.Qso
import com.log4om.android.data.refs.ActivityHit
import com.log4om.android.data.refs.MatchMethod
import com.log4om.android.ui.components.DropdownField
import com.log4om.android.ui.components.LabeledTextField
import com.log4om.android.ui.components.OsmMiniMap
import com.log4om.android.ui.components.SectionHeader
import com.log4om.android.ui.viewmodel.DxccNeededStatus
import com.log4om.android.ui.viewmodel.NewQsoViewModel
import com.log4om.android.util.AmateurRadio
import com.log4om.android.util.GridLocator
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewQsoScreen(
    viewModel: NewQsoViewModel,
    editQso: Qso? = null,
    onSaved: () -> Unit,
    onNavigateUp: () -> Unit
) {
    val form      by viewModel.form.collectAsState()
    val pastQsos  by viewModel.pastQsos.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(editQso) {
        if (editQso != null) viewModel.loadForEdit(editQso)
    }

    val msgSaved = stringResource(R.string.qso_saved)
    val msgUpdated = stringResource(R.string.qso_updated)
    LaunchedEffect(form.saveSuccess) {
        if (form.saveSuccess) {
            snackbarHostState.showSnackbar(if (form.isEditMode) msgUpdated else msgSaved)
            viewModel.clearSaveSuccess()
            if (form.isEditMode) {
                onNavigateUp()
            } else {
                viewModel.resetForm()
            }
        }
    }

    val saveErrorText = form.saveError?.asString()
    val saveErrorSnackbar = saveErrorText?.let { stringResource(R.string.error_prefix, it) }
    LaunchedEffect(saveErrorSnackbar) {
        saveErrorSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSaveError()
        }
    }

    val qrzErrorText = form.qrzError?.asString()
    val qrzErrorSnackbar = qrzErrorText?.let { stringResource(R.string.qrz_prefix, it) }
    LaunchedEffect(qrzErrorSnackbar) {
        qrzErrorSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissQrzError()
        }
    }

    val refsErrorText = form.refsCheckError?.asString()
    LaunchedEffect(refsErrorText) {
        refsErrorText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissRefsCheckError()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.checkReferencesHere()
        else {
            // ViewModel still reports need-permission if check is attempted without grant
            viewModel.checkReferencesHere()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (form.isEditMode) R.string.edit_qso else R.string.nav_new_qso
                        )
                    )
                },
                navigationIcon = {
                    if (form.isEditMode) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (!form.isEditMode) {
                        IconButton(
                            onClick = viewModel::resetForm,
                            enabled = !form.isSaving
                        ) {
                            Icon(Icons.Default.ClearAll, stringResource(R.string.clear_form))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            SectionHeader(stringResource(R.string.section_station))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = form.callsign,
                    onValueChange = viewModel::updateCallsign,
                    label = { Text(stringResource(R.string.callsign_required)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        if (form.qrzLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    isError = form.callsign.isBlank() && form.saveError != null
                )
                Button(
                    onClick = viewModel::saveQso,
                    enabled = form.callsign.isNotBlank() && !form.isSaving,
                    modifier = Modifier.height(56.dp)
                ) {
                    if (form.isSaving) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(
                                if (form.isEditMode) R.string.update else R.string.save
                            )
                        )
                    }
                }
            }

            form.qrzData?.takeIf { it.hasUsefulData }?.let { qrz ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            if (form.lookupSource.isNotBlank()) {
                                Text(
                                    stringResource(R.string.lookup_source, form.lookupSource),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (qrz.name.isNotBlank()) {
                                Text(qrz.name, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                listOf(qrz.addr2, qrz.country).filter { it.isNotBlank() }.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (qrz.grid.isNotBlank() || qrz.cqzone.isNotBlank()) {
                                Text(
                                    listOf(
                                        qrz.grid.takeIf { it.isNotBlank() }?.let { "Loc: $it" },
                                        qrz.cqzone.takeIf { it.isNotBlank() }?.let { "CQ: $it" },
                                        qrz.ituzone.takeIf { it.isNotBlank() }?.let { "ITU: $it" }
                                    ).filterNotNull().joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            when (form.dxccNeeded) {
                DxccNeededStatus.NEW_DXCC -> AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.dxcc_new)) },
                    leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) }
                )
                DxccNeededStatus.NEW_ON_BAND -> AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.dxcc_new_band)) },
                    leadingIcon = { Icon(Icons.Default.NewReleases, null, Modifier.size(18.dp)) }
                )
                DxccNeededStatus.WORKED -> AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.dxcc_worked)) },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp)) }
                )
                DxccNeededStatus.UNKNOWN -> Unit
            }

            if (form.distanceKm != null || form.bearingDeg != null || form.contactLat != null) {
                PathInfoCard(
                    distanceKm = form.distanceKm,
                    bearingDeg = form.bearingDeg,
                    lat = form.contactLat,
                    lon = form.contactLon,
                    stationLat = form.stationLat,
                    stationLon = form.stationLon,
                    callsign = form.callsign
                )
            }

            if (pastQsos.isNotEmpty()) {
                PastQsosCard(qsos = pastQsos)
            }

            SectionHeader(stringResource(R.string.section_contact))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownField(
                    label = stringResource(R.string.band),
                    value = form.band,
                    options = AmateurRadio.BANDS,
                    onSelect = viewModel::updateBand,
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = stringResource(R.string.mode),
                    value = form.mode,
                    options = AmateurRadio.MODES,
                    onSelect = viewModel::updateMode,
                    modifier = Modifier.weight(1f)
                )
            }
            LabeledTextField(
                label = stringResource(R.string.frequency),
                value = form.freq,
                onValueChange = viewModel::updateFreq,
                keyboardType = KeyboardType.Decimal
            )
            DateTimeField(
                label = stringResource(R.string.date_time),
                value = form.qsodate,
                onValueChange = viewModel::updateDate
            )

            SectionHeader(stringResource(R.string.section_rst))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabeledTextField(
                    label = stringResource(R.string.rst_sent),
                    value = form.rstsent,
                    onValueChange = viewModel::updateRstSent,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    label = stringResource(R.string.rst_rcvd),
                    value = form.rstrcvd,
                    onValueChange = viewModel::updateRstRcvd,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    label = stringResource(R.string.tx_power),
                    value = form.txpwr,
                    onValueChange = viewModel::updateTxpwr,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            SectionHeader(stringResource(R.string.section_station_data))
            LabeledTextField(stringResource(R.string.name), form.name, viewModel::updateName)
            LabeledTextField(stringResource(R.string.address), form.address, viewModel::updateAddress)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabeledTextField(
                    stringResource(R.string.qth),
                    form.qth,
                    viewModel::updateQth,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    stringResource(R.string.country),
                    form.country,
                    viewModel::updateCountry,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabeledTextField(
                    stringResource(R.string.gridsquare),
                    form.gridsquare,
                    viewModel::updateGridsquare,
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = stringResource(R.string.continent),
                    value = form.cont,
                    options = AmateurRadio.CONTINENTS,
                    onSelect = viewModel::updateCont,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabeledTextField(
                    label = stringResource(R.string.dxcc),
                    value = form.dxcc,
                    onValueChange = viewModel::updateDxcc,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    label = stringResource(R.string.cq_zone),
                    value = form.cqzone,
                    onValueChange = viewModel::updateCqzone,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    label = stringResource(R.string.itu_zone),
                    value = form.ituzone,
                    onValueChange = viewModel::updateItuzone,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            SectionHeader(stringResource(R.string.section_awards))
            OutlinedButton(
                onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                enabled = !form.refsChecking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (form.refsChecking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.refs_here))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabeledTextField(
                    stringResource(R.string.sota_ref),
                    form.sotaRef,
                    viewModel::updateSotaRef,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    stringResource(R.string.iota),
                    form.iota,
                    viewModel::updateIota,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabeledTextField(
                    stringResource(R.string.pota_ref),
                    form.potaRef,
                    viewModel::updatePotaRef,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    stringResource(R.string.wwff_ref),
                    form.wwffRef,
                    viewModel::updateWwffRef,
                    modifier = Modifier.weight(1f)
                )
            }
            LabeledTextField(
                stringResource(R.string.cota_ref),
                form.cotaRef,
                viewModel::updateCotaRef
            )
            SectionHeader(stringResource(R.string.section_extra))
            DropdownField(
                label = stringResource(R.string.prop_mode),
                value = form.propmode,
                options = AmateurRadio.PROPAGATION_MODES,
                onSelect = viewModel::updatePropmode
            )
            LabeledTextField(
                stringResource(R.string.contest_id),
                form.contestid,
                viewModel::updateContestid
            )
            LabeledTextField(
                label = stringResource(R.string.comment),
                value = form.comment,
                onValueChange = viewModel::updateComment,
                singleLine = false,
                maxLines = 3
            )
            LabeledTextField(
                label = stringResource(R.string.notes),
                value = form.notes,
                onValueChange = viewModel::updateNotes,
                singleLine = false,
                maxLines = 4
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    if (form.refsDialogOpen) {
        NearbyRefsDialog(
            hits = form.refsHits,
            onDismiss = viewModel::dismissRefsDialog,
            onApply = viewModel::applyReferenceHits
        )
    }
}

@Composable
private fun NearbyRefsDialog(
    hits: List<ActivityHit>,
    onDismiss: () -> Unit,
    onApply: (List<ActivityHit>) -> Unit
) {
    val selected = remember {
        mutableStateMapOf<String, Boolean>().apply {
            hits.forEach { put(it.ref.program.name + "|" + it.ref.reference, true) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.refs_dialog_title)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                hits.forEach { hit ->
                    val key = hit.ref.program.name + "|" + hit.ref.reference
                    val checked = selected[key] == true
                    val methodLabel = when (hit.method) {
                        MatchMethod.RADIUS -> stringResource(R.string.refs_method_radius)
                        MatchMethod.BBOX -> stringResource(R.string.refs_method_bbox)
                        MatchMethod.POLYGON -> stringResource(R.string.refs_method_polygon)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected[key] = !checked }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected[key] = it }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${hit.ref.program.name} ${hit.ref.reference}",
                                fontWeight = FontWeight.SemiBold
                            )
                            if (hit.ref.name.isNotBlank()) {
                                Text(
                                    hit.ref.name,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.refs_hit_detail,
                                    methodLabel,
                                    hit.ref.program.name,
                                    hit.distanceM,
                                    hit.radiusM
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(hits.filter {
                        selected[it.ref.program.name + "|" + it.ref.reference] == true
                    })
                }
            ) {
                Text(stringResource(R.string.refs_dialog_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun PathInfoCard(
    distanceKm: Double?,
    bearingDeg: Double?,
    lat: Double?,
    lon: Double?,
    stationLat: Double? = null,
    stationLon: Double? = null,
    callsign: String = ""
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.path_title),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
            val bits = listOfNotNull(
                distanceKm?.let { GridLocator.formatDistanceKm(it) },
                bearingDeg?.let {
                    stringResource(R.string.path_bearing, GridLocator.formatBearing(it))
                }
            )
            if (bits.isNotEmpty()) {
                Text(
                    bits.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (lat != null && lon != null) {
                OsmMiniMap(
                    contactLat = lat,
                    contactLon = lon,
                    stationLat = stationLat,
                    stationLon = stationLon,
                    contactLabel = callsign.ifBlank { "QSO" },
                    stationLabel = stringResource(R.string.map_my_qth)
                )
                Text(
                    stringResource(R.string.map_osm_credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun PastQsosCard(qsos: List<Qso>) {
    val fmt = remember { DateTimeFormatter.ofPattern("dd.MM.yy HH:mm") }
    val maxRows = 6
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.past_qsos, qsos.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            qsos.take(maxRows).forEach { q ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        q.qsodate.format(fmt) + " UTC",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(2.4f),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        q.band,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        q.mode,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            if (qsos.size > maxRows) {
                Text(
                    stringResource(R.string.past_qsos_more, qsos.size - maxRows),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    label: String,
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = value
            .toEpochSecond(java.time.ZoneOffset.UTC) * 1000
    )
    val timePickerState = rememberTimePickerState(
        initialHour   = value.hour,
        initialMinute = value.minute,
        is24Hour      = true
    )

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    val epochMs = datePickerState.selectedDateMillis ?: return@TextButton
                    val date = java.time.Instant.ofEpochMilli(epochMs)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                    onValueChange(value.with(date))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTime) {
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.time_utc)) },
            text = {
                TimePicker(
                    state = timePickerState,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTime = false
                    onValueChange(
                        value.withHour(timePickerState.hour)
                            .withMinute(timePickerState.minute)
                            .withSecond(0)
                    )
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value.format(fmt) + " UTC",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.CalendarToday, null) },
            modifier = Modifier.weight(1f)
        )
        FilledTonalIconButton(
            onClick = { showDate = true },
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Icon(Icons.Default.DateRange, stringResource(R.string.pick_date))
        }
        FilledTonalIconButton(
            onClick = { showTime = true },
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Icon(Icons.Default.Schedule, stringResource(R.string.pick_time))
        }
    }
}
