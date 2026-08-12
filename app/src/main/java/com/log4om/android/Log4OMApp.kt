package com.log4om.android

import android.app.Application
import com.log4om.android.data.db.DatabaseHelper
import com.log4om.android.data.db.QsoDao
import com.log4om.android.data.network.QrzApiService
import com.log4om.android.data.prefs.AppPrefs
import com.log4om.android.data.repository.LogRepository
import com.log4om.android.util.LocationHelper

class Log4OMApp : Application() {
    val prefs          by lazy { AppPrefs(this) }
    val dbHelper       by lazy { DatabaseHelper() }
    val qsoDao         by lazy { QsoDao(dbHelper) }
    val qrzService     by lazy { QrzApiService() }
    val repository     by lazy { LogRepository(prefs, dbHelper, qsoDao, qrzService) }
    val locationHelper by lazy { LocationHelper(this) }
}
