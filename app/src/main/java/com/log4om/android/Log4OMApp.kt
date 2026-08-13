package com.log4om.android

import android.app.Application
import com.log4om.android.data.auth.AuthTokenStore
import com.log4om.android.data.network.ClubLogApiService
import com.log4om.android.data.network.HamQthApiService
import com.log4om.android.data.network.Log4omApiService
import com.log4om.android.data.network.QrzApiService
import com.log4om.android.data.prefs.AppPrefs
import com.log4om.android.data.refs.ActivityProximityService
import com.log4om.android.data.refs.ReferenceCatalog
import com.log4om.android.data.refs.ReferenceSyncService
import com.log4om.android.data.repository.LogRepository
import com.log4om.android.util.LocationHelper
import java.io.File

class Log4OMApp : Application() {
    val prefs by lazy { AppPrefs(this) }
    val authStore by lazy { AuthTokenStore(this) }
    val api by lazy { Log4omApiService(authStore) }
    val qrzService by lazy { QrzApiService() }
    val hamqthService by lazy { HamQthApiService() }
    val clubLogService by lazy { ClubLogApiService() }
    val repository by lazy {
        LogRepository(prefs, api, qrzService, hamqthService, clubLogService)
    }
    val locationHelper by lazy { LocationHelper(this) }
    val referenceCatalog by lazy { ReferenceCatalog(File(cacheDir, "refs")) }
    val referenceSyncService by lazy { ReferenceSyncService(referenceCatalog) }
    val activityProximityService by lazy {
        ActivityProximityService(referenceCatalog, prefs, locationHelper)
    }
}
