package com.log4om.android.data.refs

/**
 * Europe (B) allowlists for catalogue sync — explicit codes, not crude lat/lon bbox.
 */
object EuropeScope {

    /** POTA `programPrefix` / `isocc` values. */
    val POTA_ISO: Set<String> = setOf(
        "AD", "AL", "AT", "AX", "BA", "BE", "BG", "BY", "CH", "CY", "CZ", "DE", "DK",
        "EE", "ES", "FI", "FO", "FR", "GB", "GG", "GI", "GR", "HR", "HU", "IE", "IM",
        "IS", "IT", "JE", "LI", "LT", "LU", "LV", "MD", "ME", "MK", "MT", "MC", "NL",
        "NO", "PL", "PT", "RO", "RS", "SE", "SI", "SJ", "SK", "SM", "UA", "VA", "XK"
    )

    /** SOTA association codes (summit prefix before `/`). */
    val SOTA_ASSOCIATIONS: Set<String> = setOf(
        "4O", "5B", "9A", "9H", "C3", "CT", "CT3", "CU",
        "DL", "DM",
        "E7", "EA1", "EA2", "EA3", "EA4", "EA5", "EA6", "EA7", "EA8", "EA9",
        "EI", "ER", "ES", "EW",
        "F", "FL",
        "G", "GD", "GI", "GJ", "GM", "GU", "GW",
        "HA", "HB", "HB0",
        "I", "IA", "IS0", "IT9",
        "JW", "JX",
        "LA", "LX", "LY", "LZ",
        "OE", "OH", "OH0", "OJ0", "OK", "OM", "ON", "OY", "OZ",
        "PA",
        "R3",
        "S5", "SM", "SP", "SV",
        "TF", "TK",
        "UT",
        "YL", "YO", "YU",
        "Z3", "ZB2"
    )

    fun sotaAssociationOf(summitCode: String): String =
        summitCode.substringBefore('/').uppercase()

    fun isEuropeanSota(summitCode: String): Boolean =
        sotaAssociationOf(summitCode) in SOTA_ASSOCIATIONS
}
