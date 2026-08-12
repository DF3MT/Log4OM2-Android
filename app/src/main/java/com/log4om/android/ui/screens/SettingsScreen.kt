package com.log4om.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.log4om.android.R
import com.log4om.android.ui.components.DropdownField
import com.log4om.android.ui.components.LabeledTextField
import com.log4om.android.ui.components.SectionHeader
import com.log4om.android.ui.viewmodel.SettingsViewModel
import com.log4om.android.ui.viewmodel.UpdateViewModel
import com.log4om.android.util.AmateurRadio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    updateViewModel: UpdateViewModel
) {
    val state      by viewModel.state.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDbPw   by remember { mutableStateOf(false) }
    var showQrzPw  by remember { mutableStateOf(false) }
    var showHamqthPw by remember { mutableStateOf(false) }

    val settingsSaved = stringResource(R.string.settings_saved)
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar(settingsSaved)
            viewModel.clearSaveSuccess()
        }
    }

    val dbTestText = state.dbTestResult?.asString()
    LaunchedEffect(dbTestText) {
        dbTestText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearDbTestResult()
        }
    }

    val importText = state.importMessage?.asString()
    LaunchedEffect(importText) {
        importText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportMessage()
        }
    }

    val context = LocalContext.current
    val adifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val input = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
        viewModel.importAdif(input)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::saveSettings,
                icon = { Icon(Icons.Default.Save, null) },
                text = {
                    Text(
                        stringResource(
                            if (state.isSaving) R.string.save_ellipsis else R.string.save
                        )
                    )
                }
            )
        }
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

            SectionHeader(stringResource(R.string.section_my_station))
            LabeledTextField(
                label = stringResource(R.string.my_callsign),
                value = state.myCallsign,
                onValueChange = viewModel::updateMyCallsign
            )
            LabeledTextField(
                label = stringResource(R.string.my_gridsquare),
                value = state.myGridsquare,
                onValueChange = viewModel::updateMyGridsquare
            )
            LabeledTextField(
                label = stringResource(R.string.my_name),
                value = state.myName,
                onValueChange = viewModel::updateMyName
            )
            LabeledTextField(
                label = stringResource(R.string.my_rig),
                value = state.myRig,
                onValueChange = viewModel::updateMyRig
            )
            LabeledTextField(
                label = stringResource(R.string.my_dxcc),
                value = state.myDxcc,
                onValueChange = viewModel::updateMyDxcc,
                keyboardType = KeyboardType.Number
            )

            SectionHeader(stringResource(R.string.section_db))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabeledTextField(
                    label = stringResource(R.string.db_host),
                    value = state.dbHost,
                    onValueChange = viewModel::updateDbHost,
                    modifier = Modifier.weight(2f)
                )
                LabeledTextField(
                    label = stringResource(R.string.db_port),
                    value = state.dbPort,
                    onValueChange = viewModel::updateDbPort,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
            LabeledTextField(
                label = stringResource(R.string.db_name),
                value = state.dbName,
                onValueChange = viewModel::updateDbName
            )
            LabeledTextField(
                label = stringResource(R.string.db_user),
                value = state.dbUser,
                onValueChange = viewModel::updateDbUser
            )
            OutlinedTextField(
                value = state.dbPassword,
                onValueChange = viewModel::updateDbPassword,
                label = { Text(stringResource(R.string.db_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showDbPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showDbPw = !showDbPw }) {
                        Icon(
                            if (showDbPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            stringResource(
                                if (showDbPw) R.string.hide_password else R.string.show_password
                            )
                        )
                    }
                }
            )
            Button(
                onClick = viewModel::testDbConnection,
                enabled = !state.isTestingDb,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isTestingDb) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.testing_connection))
                } else {
                    Icon(Icons.Default.CloudDone, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.test_connection))
                }
            }

            SectionHeader(stringResource(R.string.section_qrz))
            Text(
                stringResource(R.string.qrz_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabeledTextField(
                label = stringResource(R.string.qrz_user),
                value = state.qrzUser,
                onValueChange = viewModel::updateQrzUser
            )
            OutlinedTextField(
                value = state.qrzPassword,
                onValueChange = viewModel::updateQrzPassword,
                label = { Text(stringResource(R.string.qrz_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showQrzPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showQrzPw = !showQrzPw }) {
                        Icon(
                            if (showQrzPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            stringResource(
                                if (showQrzPw) R.string.hide_password else R.string.show_password
                            )
                        )
                    }
                }
            )

            SectionHeader(stringResource(R.string.section_hamqth))
            Text(
                stringResource(R.string.hamqth_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabeledTextField(
                label = stringResource(R.string.hamqth_user),
                value = state.hamqthUser,
                onValueChange = viewModel::updateHamqthUser
            )
            OutlinedTextField(
                value = state.hamqthPassword,
                onValueChange = viewModel::updateHamqthPassword,
                label = { Text(stringResource(R.string.hamqth_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showHamqthPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showHamqthPw = !showHamqthPw }) {
                        Icon(
                            if (showHamqthPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            stringResource(
                                if (showHamqthPw) R.string.hide_password else R.string.show_password
                            )
                        )
                    }
                }
            )

            SectionHeader(stringResource(R.string.section_clublog))
            Text(
                stringResource(R.string.clublog_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabeledTextField(
                label = stringResource(R.string.clublog_api_key),
                value = state.clublogApiKey,
                onValueChange = viewModel::updateClublogApiKey
            )

            SectionHeader(stringResource(R.string.section_adif))
            Text(
                stringResource(R.string.adif_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { adifLauncher.launch(arrayOf("*/*")) },
                enabled = !state.isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.adif_importing, state.importProgress))
                } else {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.adif_select))
                }
            }

            SectionHeader(stringResource(R.string.section_defaults))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabeledTextField(
                    label = stringResource(R.string.rst_sent),
                    value = state.defaultRstSent,
                    onValueChange = viewModel::updateDefaultRstSent,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    label = stringResource(R.string.rst_rcvd),
                    value = state.defaultRstRcvd,
                    onValueChange = viewModel::updateDefaultRstRcvd,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    label = stringResource(R.string.tx_power),
                    value = state.defaultTxpwr,
                    onValueChange = viewModel::updateDefaultTxpwr,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownField(
                    label = stringResource(R.string.default_band),
                    value = state.defaultBand,
                    options = AmateurRadio.BANDS,
                    onSelect = viewModel::updateDefaultBand,
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = stringResource(R.string.default_mode),
                    value = state.defaultMode,
                    options = AmateurRadio.MODES,
                    onSelect = viewModel::updateDefaultMode,
                    modifier = Modifier.weight(1f)
                )
            }

            SectionHeader(stringResource(R.string.section_updates))
            Text(
                stringResource(
                    R.string.update_current_version,
                    updateState.currentVersionName,
                    updateState.currentVersionCode
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { updateViewModel.checkForUpdates(silentIfUpToDate = false) },
                enabled = !updateState.isChecking && !updateState.isDownloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (updateState.isChecking) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.update_checking))
                } else {
                    Icon(Icons.Default.SystemUpdate, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.update_check_now))
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
