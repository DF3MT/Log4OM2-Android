package com.log4om.api.lookup

import org.springframework.stereotype.Component

interface CallbookProbe {
    fun testQrz(user: String, password: String, callsign: String): LookupHit
    fun testHamqth(user: String, password: String, callsign: String): LookupHit
    fun testClubLog(apiKey: String, callsign: String): LookupHit
}

@Component
class HttpCallbookProbe : CallbookProbe by CallbookClient(JdkLookupHttp())
