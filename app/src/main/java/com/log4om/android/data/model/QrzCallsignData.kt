package com.log4om.android.data.model

data class QrzCallsignData(
    val call: String = "",
    val fname: String = "",
    val name: String = "",
    val addr1: String = "",
    val addr2: String = "",
    val state: String = "",
    val country: String = "",
    val cqzone: String = "",
    val ituzone: String = "",
    val lat: String = "",
    val lon: String = "",
    val grid: String = "",
    val email: String = "",
    val dxcc: String = "",
    val continent: String = "",
    val pfx: String = "",
    val bio: String = "",
    val image: String = "",
    val error: String? = null
) {
    val fullName: String get() = listOf(fname, name).filter { it.isNotBlank() }.joinToString(" ")
    val cityCountry: String get() = listOf(addr2, country).filter { it.isNotBlank() }.joinToString(", ")
}
