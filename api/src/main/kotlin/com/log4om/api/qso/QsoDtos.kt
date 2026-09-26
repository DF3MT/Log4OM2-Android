package com.log4om.api.qso

import java.time.LocalDate
import java.time.LocalDateTime

data class QsoDto(
    val qsoid: Long? = null,
    val callsign: String = "",
    val band: String = "",
    val mode: String = "",
    val qsodate: LocalDateTime = LocalDateTime.now(),
    val freq: Double = 0.0,
    val freqrx: Double = 0.0,
    val rstsent: String = "59",
    val rstrcvd: String = "59",
    val name: String = "",
    val address: String = "",
    val qth: String = "",
    val country: String = "",
    val dxcc: Int = 0,
    val cqzone: Int? = null,
    val ituzone: Int? = null,
    val gridsquare: String = "",
    val cont: String = "",
    val comment: String = "",
    val notes: String = "",
    val txpwr: Double? = null,
    val propmode: String = "",
    val contestid: String = "",
    val satmode: String = "",
    val satname: String = "",
    val satelliteqso: Int = 0,
    val stationcallsign: String = "",
    val mygridsquare: String = "",
    val myname: String = "",
    val myrig: String = "",
    val mycountry: String = "",
    val mydxcc: Int? = null,
    val mylat: Double? = null,
    val mylon: Double? = null,
    val operator: String = "",
    val bandrx: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val distance: Double? = null,
    val sotaRef: String = "",
    val iota: String = "",
    val potaRef: String = "",
    val wwffRef: String = "",
    val cotaRef: String = "",
    val programid: String = "Log4OM API",
    val programversion: String = "0.1.0"
)

data class LogFilterDto(
    val callsign: String = "",
    val band: String = "",
    val mode: String = "",
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val country: String = "",
    val dxcc: String = "",
    val sotaRef: String = "",
    val iota: String = "",
    val potaRef: String = "",
    val wwffRef: String = "",
    val cotaRef: String = ""
)

data class BulkInsertResultDto(val inserted: Int, val skipped: Int)

data class IdsRequest(val ids: List<Long>)
