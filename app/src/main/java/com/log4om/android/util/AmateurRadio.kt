package com.log4om.android.util

object AmateurRadio {

    val BANDS = listOf(
        "2200m", "630m", "160m", "80m", "60m", "40m", "30m", "20m",
        "17m", "15m", "12m", "10m", "6m", "4m", "2m", "1.25m",
        "70cm", "33cm", "23cm", "13cm", "9cm", "6cm", "3cm"
    )

    val MODES = listOf(
        "SSB", "CW", "FM", "AM",
        "FT8", "FT4", "JS8", "JT65", "JT9", "JT4", "Q65", "QRA64", "MSK144",
        "WSPR", "FSK441", "ISCAT",
        "RTTY", "PSK31", "PSK63", "PSK125", "OLIVIA", "MFSK16", "HELL",
        "SSTV", "ATV",
        "PACKET", "PACTOR", "WINMOR",
        "DMR", "D-STAR", "C4FM", "P25"
    )

    val PROPAGATION_MODES = listOf(
        "", "AS", "AUE", "AUR", "BS", "ECH", "EME", "ES", "F2", "FAI",
        "GRND", "ION", "IRL", "LOS", "MS", "RPT", "RS", "SAT", "TEP", "TR"
    )

    val CONTINENTS = listOf("", "AF", "AN", "AS", "EU", "NA", "OC", "SA")

    val BAND_FREQUENCIES = mapOf(
        "160m" to 1.850,
        "80m" to 3.700,
        "60m" to 5.357,
        "40m" to 7.100,
        "30m" to 10.136,
        "20m" to 14.225,
        "17m" to 18.130,
        "15m" to 21.200,
        "12m" to 24.930,
        "10m" to 28.400,
        "6m" to 50.150,
        "4m" to 70.200,
        "2m" to 144.300,
        "1.25m" to 222.100,
        "70cm" to 432.100,
        "33cm" to 902.100,
        "23cm" to 1296.100
    )

    fun defaultRstForMode(mode: String): String = when {
        mode == "CW" -> "599"
        mode in listOf("FT8", "FT4", "JS8", "JT65", "JT9", "JT4", "Q65", "QRA64", "MSK144", "WSPR") -> "59"
        else -> "59"
    }

    fun freqForBand(band: String): String {
        val freq = BAND_FREQUENCIES[band] ?: return ""
        return freq.toString()
    }
}
