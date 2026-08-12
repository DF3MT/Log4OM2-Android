package com.log4om.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.log4om.android.R
import com.log4om.android.data.model.AppUpdateInfo

@Composable
fun UpdateAvailableDialog(
    update: AppUpdateInfo,
    currentVersionName: String,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onOpenBrowser: () -> Unit,
    onInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(
                        R.string.update_available_message,
                        update.versionName,
                        currentVersionName
                    )
                )
                if (update.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.update_release_notes),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(update.releaseNotes.take(800))
                }
                if (isDownloading) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.update_downloading))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onInstall,
                enabled = !isDownloading
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(Modifier.height(18.dp))
                } else {
                    Text(stringResource(R.string.update_install_now))
                }
            }
        },
        dismissButton = {
            Column {
                TextButton(
                    onClick = onOpenBrowser,
                    enabled = !isDownloading
                ) {
                    Text(stringResource(R.string.update_open_browser))
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isDownloading
                ) {
                    Text(stringResource(R.string.update_later))
                }
            }
        }
    )
}
