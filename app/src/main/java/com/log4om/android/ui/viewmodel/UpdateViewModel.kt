package com.log4om.android.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.log4om.android.R
import com.log4om.android.data.model.AppUpdateInfo
import com.log4om.android.data.network.GitHubUpdateService
import com.log4om.android.ui.util.UiText
import com.log4om.android.util.ApkInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgressNote: UiText? = null,
    val availableUpdate: AppUpdateInfo? = null,
    val showDialog: Boolean = false,
    val message: UiText? = null,
    val currentVersionName: String = "",
    val currentVersionCode: Int = 0
)

class UpdateViewModel(
    application: Application,
    private val updateService: GitHubUpdateService = GitHubUpdateService()
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        val (name, code) = currentAppVersion()
        _state.update { it.copy(currentVersionName = name, currentVersionCode = code) }
    }

    fun checkForUpdates(silentIfUpToDate: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(isChecking = true, message = null)
            }
            updateService.fetchLatestRelease().fold(
                onSuccess = { info ->
                    val current = _state.value.currentVersionCode
                    if (info.versionCode > current) {
                        _state.update {
                            it.copy(
                                isChecking = false,
                                availableUpdate = info,
                                showDialog = true
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isChecking = false,
                                availableUpdate = null,
                                showDialog = false,
                                message = if (silentIfUpToDate) null
                                else UiText.Resource(R.string.update_up_to_date)
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isChecking = false,
                            message = if (silentIfUpToDate) null
                            else UiText.Resource(
                                R.string.update_check_failed,
                                e.localizedMessage.orEmpty()
                            )
                        )
                    }
                }
            )
        }
    }

    fun dismissDialog() = _state.update { it.copy(showDialog = false) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun openInBrowser() {
        val url = _state.value.availableUpdate?.htmlUrl ?: return
        ApkInstaller.openUrl(getApplication(), url)
        dismissDialog()
    }

    fun downloadAndInstall() {
        val update = _state.value.availableUpdate ?: return
        val context = getApplication<Application>()
        if (!ApkInstaller.canInstallPackages(context)) {
            _state.update {
                it.copy(message = UiText.Resource(R.string.update_allow_unknown_apps))
            }
            runCatching {
                context.startActivity(
                    ApkInstaller.installPermissionSettingsIntent(context)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isDownloading = true,
                    downloadProgressNote = UiText.Resource(R.string.update_downloading)
                )
            }
            val target = File(context.cacheDir, "updates/Log4OM-Android-update.apk")
            updateService.downloadApk(update.apkDownloadUrl, target).fold(
                onSuccess = { file ->
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgressNote = null,
                            showDialog = false
                        )
                    }
                    runCatching { ApkInstaller.installApk(context, file) }
                        .onFailure { e ->
                            _state.update {
                                it.copy(
                                    message = UiText.Resource(
                                        R.string.update_install_failed,
                                        e.localizedMessage.orEmpty()
                                    )
                                )
                            }
                        }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgressNote = null,
                            message = UiText.Resource(
                                R.string.update_download_failed,
                                e.localizedMessage.orEmpty()
                            )
                        )
                    }
                }
            )
        }
    }

    private fun currentAppVersion(): Pair<String, Int> {
        val context = getApplication<Application>()
        return try {
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
            (info.versionName ?: "1.0.0") to code
        } catch (_: Exception) {
            "1.0.0" to 1
        }
    }
}
