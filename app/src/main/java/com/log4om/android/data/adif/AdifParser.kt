package com.log4om.android.data.adif

import java.io.InputStream

/**
 * Minimal ADIF (Amateur Data Interchange Format) parser.
 * Supports both .adi (text) files.
 * Tokens: `<FIELD:LENGTH[:TYPE]>VALUE` … `<EOR>`. Header optional, ends with `<EOH>`.
 */
object AdifParser {

    fun parse(input: InputStream): Sequence<Map<String, String>> {
        val text = input.bufferedReader(Charsets.UTF_8).readText()
        return parse(text)
    }

    fun parse(text: String): Sequence<Map<String, String>> = sequence {
        var pos = 0
        val headerEnd = findTag(text, "EOH", 0)
        if (headerEnd >= 0) pos = headerEnd
        while (pos < text.length) {
            val next = parseRecord(text, pos) ?: break
            if (next.first.isNotEmpty()) yield(next.first)
            pos = next.second
        }
    }

    private fun findTag(text: String, tag: String, from: Int): Int {
        val needle = "<${tag.lowercase()}>"
        val idx = text.lowercase().indexOf(needle, from)
        return if (idx >= 0) idx + needle.length else -1
    }

    private fun parseRecord(text: String, start: Int): Pair<Map<String, String>, Int>? {
        val record = mutableMapOf<String, String>()
        var pos = start
        while (pos < text.length) {
            val lt = text.indexOf('<', pos)
            if (lt < 0) return if (record.isNotEmpty()) record to text.length else null
            val gt = text.indexOf('>', lt)
            if (gt < 0) return if (record.isNotEmpty()) record to text.length else null
            val tag = text.substring(lt + 1, gt)
            if (tag.equals("EOR", ignoreCase = true)) {
                return record to (gt + 1)
            }
            if (tag.equals("EOH", ignoreCase = true)) {
                pos = gt + 1
                continue
            }
            val parts = tag.split(':')
            if (parts.size < 2) { pos = gt + 1; continue }
            val name = parts[0].trim().uppercase()
            val len  = parts[1].trim().toIntOrNull() ?: 0
            if (len > 0 && gt + 1 + len <= text.length) {
                val value = text.substring(gt + 1, gt + 1 + len)
                record[name] = value
                pos = gt + 1 + len
            } else {
                pos = gt + 1
            }
        }
        return if (record.isNotEmpty()) record to pos else null
    }
}
